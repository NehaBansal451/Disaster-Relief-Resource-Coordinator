package com.disasterrelief.backend.allocation;

import com.disasterrelief.backend.alert.AlertService;
import com.disasterrelief.backend.allocation.AllocationDtos.AllocationResponse;
import com.disasterrelief.backend.allocation.AllocationDtos.ApproveRequest;
import com.disasterrelief.backend.allocation.AllocationDtos.ItemEdit;
import com.disasterrelief.backend.allocation.AllocationDtos.ItemResponse;
import com.disasterrelief.backend.allocation.AllocationDtos.NotSelected;
import com.disasterrelief.backend.audit.AuditLogger;
import com.disasterrelief.backend.common.ApiException;
import com.disasterrelief.backend.common.FieldProblem;
import com.disasterrelief.backend.inventory.InventoryRepository;
import com.disasterrelief.backend.request.RequestRepository;
import com.disasterrelief.backend.request.RequestStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recommend, approve and reject allocations. Approval is the integrity-critical step (blueprint 7.2): inside ONE
 * transaction it locks the allocation row, then locks the affected inventory rows in id order, re-checks the stock,
 * reserves it and moves the request APPROVED -> ALLOCATED. If anything fails the whole transaction rolls back, so stock
 * can never be reserved twice or go negative (the database CHECK reserved <= on_hand is the last line of defence).
 */
@Service
public class AllocationService {

    private final AllocationRepository repository;
    private final RequestRepository requests;
    private final InventoryRepository inventory;
    private final AuditLogger audit;
    private final AlertService alerts;

    public AllocationService(AllocationRepository repository, RequestRepository requests,
                             InventoryRepository inventory, AuditLogger audit, AlertService alerts) {
        this.repository = repository;
        this.requests = requests;
        this.inventory = inventory;
        this.audit = audit;
        this.alerts = alerts;
    }

    @Transactional(readOnly = true)
    public AllocationResponse get(long id) {
        return toResponse(repository.find(id).orElseThrow(() -> ApiException.notFound("Allocation " + id + " was not found.")));
    }

    @Transactional(readOnly = true)
    public List<AllocationResponse> forRequest(long requestId) {
        return repository.forRequest(requestId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public AllocationResponse recommend(long requestId, long userId) {
        AllocationRepository.RequestFacts facts = repository.requestFacts(requestId)
                .orElseThrow(() -> ApiException.notFound("Request " + requestId + " was not found."));
        if (!RequestStatus.APPROVED.name().equals(facts.status())) {
            throw ApiException.invalidState("Request " + requestId + " is " + facts.status()
                    + "; an allocation can only be recommended for an APPROVED request.");
        }
        if (repository.hasApprovedAllocation(requestId)) {
            throw ApiException.conflict("ALREADY_ALLOCATED", "Request " + requestId + " already has an approved allocation.");
        }
        repository.markStale(requestId);

        List<AllocationRules.Candidate> candidates = repository.candidates(facts.resourceId(), facts.areaId());
        int competing = repository.competingDemand(requestId, facts.resourceId());
        AllocationRules.Plan plan = AllocationRules.recommend(facts.quantity(), competing, candidates);

        long id = repository.insertAllocation(requestId, facts.quantity(), plan.allocated(), plan.explanation(), userId);
        for (AllocationRules.Pick pick : plan.picks()) {
            repository.insertItem(id, pick.warehouseId(), pick.quantity(), pick.distanceKm(), pick.reason());
        }
        for (AllocationRules.Skipped skipped : plan.skipped()) {
            repository.insertNotSelected(id, skipped.warehouseId(), skipped.reason());
        }
        List<Long> withStock = candidates.stream().map(AllocationRules.Candidate::warehouseId).toList();
        for (NotSelected none : repository.warehousesWithoutStock(withStock)) {
            repository.insertNotSelected(id, none.warehouseId(), none.reason());
        }
        audit.log(userId, "ALLOCATION_RECOMMENDED", "allocations", id,
                "request " + requestId + ", allocated " + plan.allocated() + " of " + facts.quantity());
        return get(id);
    }

    @Transactional
    public AllocationResponse approve(long id, ApproveRequest body, long officerId) {
        AllocationRepository.Row allocation = repository.lock(id)
                .orElseThrow(() -> ApiException.notFound("Allocation " + id + " was not found."));
        if ("STALE".equals(allocation.status())) {
            throw ApiException.conflict("STALE_RECOMMENDATION",
                    "Allocation " + id + " is stale. Request a new recommendation.");
        }
        if (!"RECOMMENDED".equals(allocation.status())) {
            throw ApiException.invalidState("Allocation " + id + " is " + allocation.status() + " and cannot be approved.");
        }
        if (allocation.version() != body.version()) {
            throw ApiException.conflict("STALE_VERSION", "Allocation " + id + " changed (version " + allocation.version()
                    + "). Reload it and try again.");
        }
        AllocationRepository.RequestFacts facts = repository.requestFacts(allocation.requestId()).orElseThrow();
        if (!RequestStatus.APPROVED.name().equals(facts.status())) {
            throw ApiException.invalidState("Request " + allocation.requestId() + " is " + facts.status()
                    + "; only an APPROVED request can be allocated.");
        }

        boolean edited = body.items() != null && !body.items().isEmpty();
        List<ItemEdit> plan = edited ? body.items() : repository.items(id).stream()
                .map(i -> new ItemEdit(i.warehouseId(), i.quantity())).toList();
        validatePlan(plan, facts.quantity());

        List<Long> warehouseIds = plan.stream().map(ItemEdit::warehouseId).sorted().toList();
        List<AllocationRepository.Line> lines = repository.lockLines(facts.resourceId(), warehouseIds);
        int total = 0;
        for (ItemEdit item : plan.stream().sorted(Comparator.comparingLong(ItemEdit::warehouseId)).toList()) {
            List<AllocationRepository.Line> own = lines.stream().filter(l -> l.warehouseId() == item.warehouseId())
                    .sorted(Comparator.comparing(AllocationRepository.Line::expiry, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparingLong(AllocationRepository.Line::inventoryId)).toList();
            int need = item.quantity();
            for (AllocationRepository.Line line : own) {
                if (need == 0) {
                    break;
                }
                int take = Math.min(need, line.available());
                if (take <= 0) {
                    continue;
                }
                AllocationRepository.Balance balance = repository.reserve(line.inventoryId(), take)
                        .orElseThrow(() -> insufficient(item.warehouseId()));
                repository.insertReservation(id, line.inventoryId(), take);
                inventory.addLedger(line.inventoryId(), "RESERVATION", 0, take, allocation.requestId(),
                        "Allocation " + id, null, officerId);
                if (AlertService.isLowStock(balance.onHand(), balance.reserved())) {
                    alerts.raise("LOW_STOCK", "HIGH", "Low stock: inventory line " + line.inventoryId() + " has "
                            + (balance.onHand() - balance.reserved()) + " of " + balance.onHand() + " left.",
                            "inventory", line.inventoryId());
                }
                need -= take;
            }
            if (need > 0) {
                throw insufficient(item.warehouseId());
            }
            total += item.quantity();
        }

        if (edited) {
            repository.deleteItems(id);
            for (ItemEdit item : plan) {
                repository.insertItem(id, item.warehouseId(), item.quantity(), null, "Quantity set by the approving officer.");
            }
        }
        String note = body.note() == null || body.note().isBlank() ? null : body.note().trim();
        repository.markApproved(id, officerId, note, total);
        if (!requests.transition(allocation.requestId(), RequestStatus.APPROVED, RequestStatus.ALLOCATED, null, null)) {
            throw ApiException.invalidState("Request " + allocation.requestId() + " is no longer APPROVED.");
        }
        requests.addHistory(allocation.requestId(), "ALLOCATED", RequestStatus.APPROVED, RequestStatus.ALLOCATED,
                officerId, "Allocation " + id + ": " + total + " of " + facts.quantity() + " reserved");
        audit.log(officerId, "ALLOCATION_APPROVED", "allocations", id,
                "request " + allocation.requestId() + ", reserved " + total + " of " + facts.quantity());
        return get(id);
    }

    @Transactional
    public AllocationResponse reject(long id, String note, long officerId) {
        AllocationRepository.Row allocation = repository.lock(id)
                .orElseThrow(() -> ApiException.notFound("Allocation " + id + " was not found."));
        if (!"RECOMMENDED".equals(allocation.status())) {
            throw ApiException.invalidState("Allocation " + id + " is " + allocation.status() + " and cannot be rejected.");
        }
        repository.markRejected(id, note == null || note.isBlank() ? null : note.trim());
        audit.log(officerId, "ALLOCATION_REJECTED", "allocations", id, note);
        return get(id);
    }

    private void validatePlan(List<ItemEdit> plan, int requested) {
        List<FieldProblem> errors = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        int sum = 0;
        for (ItemEdit item : plan) {
            if (!seen.add(item.warehouseId())) {
                errors.add(new FieldProblem("items", "Warehouse " + item.warehouseId() + " appears more than once."));
            }
            sum += item.quantity();
        }
        if (plan.isEmpty() || sum <= 0) {
            errors.add(new FieldProblem("items", "There is nothing to allocate: no warehouse has stock for this request."));
        }
        if (sum > requested) {
            errors.add(new FieldProblem("items", "Allocated quantity " + sum + " is more than the requested " + requested + "."));
        }
        if (errors.isEmpty() && repository.activeWarehouseIds(new ArrayList<>(seen)).size() != seen.size()) {
            errors.add(new FieldProblem("items", "Every warehouse must exist and be active."));
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
    }

    private static ApiException insufficient(long warehouseId) {
        return ApiException.conflict("INSUFFICIENT_STOCK", "Warehouse " + warehouseId
                + " no longer has enough available stock. Request a new recommendation.");
    }

    private AllocationResponse toResponse(AllocationRepository.Row r) {
        List<ItemResponse> items = repository.items(r.id());
        return new AllocationResponse(r.id(), r.requestId(), r.status(), r.requestedQty(), r.allocatedQty(),
                r.requestedQty() - r.allocatedQty(), items, repository.notSelected(r.id()), r.explanation(), r.version(),
                r.note(), r.approvedBy(), r.approvedAt());
    }
}
