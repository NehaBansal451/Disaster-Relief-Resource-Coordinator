package com.disasterrelief.backend.inventory;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Body for POST /api/inventory/receipts. expiryDate is required when the resource is perishable. */
public record ReceiptRequest(
        @NotNull Long warehouseId,
        @NotNull Long resourceId,
        @NotNull @Min(1) @Max(10_000_000) Integer quantity,
        @Size(max = 50) String batchRef,
        LocalDate expiryDate,
        @Size(max = 500) String note) {
}
