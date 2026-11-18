package com.disasterrelief.backend.allocation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

/** Request and response shapes for /api/allocations (docs/API_CONTRACT.md section 4). */
public final class AllocationDtos {

    private AllocationDtos() {
    }

    public record RecommendRequest(@NotNull Long requestId) {
    }

    public record ItemEdit(@NotNull Long warehouseId, @NotNull @Min(1) @Max(10_000_000) Integer quantity) {
    }

    /** items is optional: when present it replaces the recommended split (the officer edits it). */
    public record ApproveRequest(@NotNull Integer version, @Valid List<ItemEdit> items, @Size(max = 1000) String note) {
    }

    public record NoteRequest(@Size(max = 1000) String note) {
    }

    public record ItemResponse(long warehouseId, String warehouseName, int quantity, Double distanceKm, String reason) {
    }

    public record NotSelected(long warehouseId, String warehouseName, String reason) {
    }

    public record AllocationResponse(long id, long requestId, String status, int requestedQty, int allocatedQty,
                                     int unfilledQty, List<ItemResponse> items, List<NotSelected> notSelected,
                                     String explanation, int version, String note, Long approvedBy,
                                     OffsetDateTime approvedAt) {
    }
}
