package com.disasterrelief.backend.inventory;

import com.disasterrelief.backend.common.PageResponse;
import com.disasterrelief.backend.security.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** /api/inventory. Read: ADMIN, OFFICER, WAREHOUSE_MANAGER (contract). Receipts: ADMIN, WAREHOUSE_MANAGER (assumption S1). */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OFFICER','WAREHOUSE_MANAGER')")
    public PageResponse<InventoryRow> list(@RequestParam(required = false) Long warehouseId,
                                           @RequestParam(required = false) Long resourceId,
                                           @RequestParam(required = false) Boolean expiringOnly,
                                           @RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size,
                                           @RequestParam(required = false) String sort) {
        return service.list(warehouseId, resourceId, expiringOnly, page, size, sort);
    }

    @GetMapping("/{id}/transactions")
    @PreAuthorize("hasAnyRole('ADMIN','OFFICER','WAREHOUSE_MANAGER')")
    public List<LedgerEntry> transactions(@PathVariable long id) {
        return service.ledger(id);
    }

    @PostMapping("/receipts")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE_MANAGER')")
    public ResponseEntity<InventoryRow> receive(@Valid @RequestBody ReceiptRequest body, @AuthenticationPrincipal Jwt jwt) {
        boolean admin = "ADMIN".equals(jwt.getClaimAsString("role"));
        InventoryRow row = service.receive(body, CurrentUser.id(jwt), admin);
        return ResponseEntity.created(URI.create("/api/inventory?warehouseId=" + row.warehouseId())).body(row);
    }
}
