package com.disasterrelief.backend.dispatch;

import com.disasterrelief.backend.alert.AlertService;
import com.disasterrelief.backend.audit.AuditLogger;
import com.disasterrelief.backend.common.ApiException;
import com.disasterrelief.backend.common.PageQuery;
import com.disasterrelief.backend.common.PageResponse;
import com.disasterrelief.backend.dispatch.DispatchDtos.CreateRequest;
import com.disasterrelief.backend.dispatch.DispatchDtos.DispatchResponse;
import com.disasterrelief.backend.dispatch.DispatchDtos.StatusRequest;
import com.disasterrelief.backend.inventory.InventoryRepository;
import com.disasterrelief.backend.request.RequestRepository;
import com.disasterrelief.backend.request.RequestStatus;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dispatch and delivery. Creating a dispatch moves the reserved stock out of the warehouse (on hand and reserved both
 * go down, DISPATCH ledger row) in one transaction; status changes keep the request status and timeline in step.
 */
@Service
public class DispatchService {

    private final DispatchRepository repository;
    private final RequestRepository requests;
    private final InventoryRepository inventory;
    private final AuditLogger audit;
    private final AlertService alerts;

    public DispatchService(DispatchRepository repository, RequestRepository requests, InventoryRepository inventory,
                           AuditLogger audit, AlertService alerts) {
        this.repository = repository;
        this.requests = requests;
        this.inventory = inventory;
        this.audit = audit;
        this.alerts = alerts;
    }

    @Transactional(readOnly = true)
    public DispatchResponse get(long id) {
        return repository.find(id).orElseThrow(() -> ApiException.notFound("Dispatch " + id + " was not found."));
    }

    @Transactional(readOnly = true)
    public PageResponse<DispatchResponse> list(String status, Long requestId, Long warehouseId, Integer page,
                                               Integer size, String sort) {
        String s = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        PageQuery query = PageQuery.parse(page, size, sort, DispatchRepository.SORTS, "dispatchedAt", true);
        return repository.search(s, requestId, warehouseId, query);
    }

    @Transactional
    public DispatchResponse create(CreateRequest body, long userId, boolean administrator) {
        DispatchRepository.AllocationFacts facts = repository.allocationFacts(body.allocationId())
                .orElseThrow(() -> ApiException.validation("allocationId", "Allocation not found."));
        if (!"APPROVED".equals(facts.status())) {
            throw ApiException.invalidState("Allocation " + body.allocationId() + " is " + facts.status()
                    + "; only an APPROVED allocation can be dispatched.");
        }
        if (!repository.warehouseInAllocation(body.allocationId(), body.warehouseId())) {
            throw ApiException.validation("warehouseId", "This warehouse is not part of the allocation.");
        }
        InventoryRepository.WarehouseInfo warehouse = inventory.warehouse(body.warehouseId()).orElseThrow();
        if (!administrator && !Long.valueOf(userId).equals(warehouse.managerId())) {
            throw new AccessDeniedException("You can only dispatch from the warehouse you manage.");
        }
        if (repository.exists(body.allocationId(), body.warehouseId())) {
            throw ApiException.conflict("ALREADY_DISPATCHED", "This warehouse has already dispatched for this allocation.");
        }
        List<DispatchRepository.Reservation> reservations = repository.lockReservations(body.allocationId(), body.warehouseId());
        if (reservations.isEmpty()) {
            throw ApiException.conflict("NOTHING_TO_DISPATCH", "There is no reserved stock left to dispatch from this warehouse.");
        }
        int total = 0;
        for (DispatchRepository.Reservation r : reservations) {
            DispatchRepository.Balance balance = repository.deduct(r.inventoryId(), r.quantity()).orElseThrow(
                    () -> ApiException.conflict("INSUFFICIENT_STOCK", "Reserved stock is missing on inventory line " + r.inventoryId() + "."));
            inventory.addLedger(r.inventoryId(), "DISPATCH", -r.quantity(), -r.quantity(), facts.requestId(),
                    "Dispatch for allocation " + body.allocationId(), null, userId);
            repository.markDispatched(r.id());
            total += r.quantity();
        }
        String note = blankToNull(body.note());
        long id = repository.insert(body.allocationId(), facts.requestId(), body.warehouseId(), total,
                blankToNull(body.vehicle()), blankToNull(body.driverName()), note, userId);
        repository.addUpdate(id, "DISPATCHED", note, userId);
        if (requests.transition(facts.requestId(), RequestStatus.ALLOCATED, RequestStatus.DISPATCHED, null, null)) {
            requests.addHistory(facts.requestId(), "DISPATCHED", RequestStatus.ALLOCATED, RequestStatus.DISPATCHED, userId,
                    total + " dispatched from warehouse " + body.warehouseId());
        }
        alerts.raise("DISPATCHED", "LOW", "Dispatch #" + id + " left warehouse " + body.warehouseId() + " for request #"
                + facts.requestId() + ".", "dispatches", id);
        audit.log(userId, "DISPATCH_CREATED", "dispatches", id, "request " + facts.requestId() + ", quantity " + total);
        return get(id);
    }

    @Transactional
    public DispatchResponse updateStatus(long id, StatusRequest body, long userId, String role) {
        String target = DispatchRules.requestedStatus(body.status());
        DispatchRepository.Locked dispatch = repository.lock(id)
                .orElseThrow(() -> ApiException.notFound("Dispatch " + id + " was not found."));
        if ("WAREHOUSE_MANAGER".equals(role)) {
            InventoryRepository.WarehouseInfo warehouse = inventory.warehouse(dispatch.warehouseId()).orElseThrow();
            if (!Long.valueOf(userId).equals(warehouse.managerId())) {
                throw new AccessDeniedException("You can only update dispatches of the warehouse you manage.");
            }
        }
        if (!DispatchRules.canMove(dispatch.status(), target)) {
            throw ApiException.invalidState("Dispatch " + id + " is " + dispatch.status() + " and cannot move to " + target + ".");
        }
        String note = blankToNull(body.note());
        repository.setStatus(id, target);
        repository.addUpdate(id, target, note, userId);

        if ("IN_TRANSIT".equals(target)) {
            if (requests.transition(dispatch.requestId(), RequestStatus.DISPATCHED, RequestStatus.IN_TRANSIT, null, null)) {
                requests.addHistory(dispatch.requestId(), "IN_TRANSIT", RequestStatus.DISPATCHED, RequestStatus.IN_TRANSIT,
                        userId, "Dispatch " + id + " is on the way");
            }
        } else {
            alerts.raise("DELIVERED", "LOW", "Dispatch #" + id + " was delivered for request #" + dispatch.requestId() + ".",
                    "dispatches", id);
            if (repository.deliveredCount(dispatch.allocationId()) == repository.itemCount(dispatch.allocationId())
                    && requests.transition(dispatch.requestId(), RequestStatus.IN_TRANSIT, RequestStatus.DELIVERED, null, null)) {
                requests.addHistory(dispatch.requestId(), "DELIVERED", RequestStatus.IN_TRANSIT, RequestStatus.DELIVERED,
                        userId, "All dispatches of allocation " + dispatch.allocationId() + " delivered");
            }
        }
        audit.log(userId, "DISPATCH_" + target, "dispatches", id, note);
        return get(id);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
