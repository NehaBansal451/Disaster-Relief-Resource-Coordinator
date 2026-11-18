package com.disasterrelief.backend.inventory;

import com.disasterrelief.backend.common.ApiException;
import com.disasterrelief.backend.common.FieldProblem;
import com.disasterrelief.backend.common.PageQuery;
import com.disasterrelief.backend.common.PageResponse;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final InventoryRepository repository;

    public InventoryService(InventoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryRow> list(Long warehouseId, Long resourceId, Boolean expiringOnly,
                                           Integer page, Integer size, String sort) {
        PageQuery query = PageQuery.parse(page, size, sort, InventoryRepository.SORTS, "resource", false);
        return repository.search(warehouseId, resourceId, Boolean.TRUE.equals(expiringOnly), query);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> ledger(long inventoryId) {
        if (!repository.exists(inventoryId)) {
            throw ApiException.notFound("Inventory line " + inventoryId + " was not found.");
        }
        return repository.ledger(inventoryId, 100);
    }

    /**
     * Receives stock into a warehouse and writes the ledger row in the same transaction. A warehouse manager may
     * only receive into the warehouse they manage; an administrator may receive anywhere.
     */
    @Transactional
    public InventoryRow receive(ReceiptRequest body, long userId, boolean administrator) {
        InventoryRepository.WarehouseInfo warehouse = repository.warehouse(body.warehouseId()).orElse(null);
        Boolean perishable = repository.activeResourcePerishable(body.resourceId()).orElse(null);
        List<FieldProblem> errors = new java.util.ArrayList<>();
        if (warehouse == null) {
            errors.add(new FieldProblem("warehouseId", "Warehouse not found."));
        } else if (!warehouse.active()) {
            errors.add(new FieldProblem("warehouseId", "Warehouse is inactive."));
        }
        if (perishable == null) {
            errors.add(new FieldProblem("resourceId", "Resource not found or no longer active."));
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        if (!administrator && !Long.valueOf(userId).equals(warehouse.managerId())) {
            throw new AccessDeniedException("You can only receive stock into the warehouse you manage.");
        }
        InventoryRules.validateReceipt(perishable, body.expiryDate(), InventoryRules.today());

        String batch = InventoryRules.normalizeBatch(body.batchRef());
        if (perishable && batch == null) {
            throw ApiException.validation("batchRef", "batchRef is required for a perishable resource.");
        }
        InventoryRepository.Receipt receipt = repository
                .addStock(body.warehouseId(), body.resourceId(), batch, body.quantity(), body.expiryDate())
                .orElseThrow(() -> ApiException.conflict("BATCH_EXPIRY_MISMATCH",
                        "Batch " + (batch == null ? "(none)" : batch) + " already exists with a different expiry date. Use a new batch reference."));
        String note = body.note() == null || body.note().isBlank() ? null : body.note().trim();
        repository.addLedger(receipt.inventoryId(), "RECEIPT", body.quantity(), 0, null, note, null, userId);
        return repository.findById(receipt.inventoryId()).orElseThrow();
    }
}
