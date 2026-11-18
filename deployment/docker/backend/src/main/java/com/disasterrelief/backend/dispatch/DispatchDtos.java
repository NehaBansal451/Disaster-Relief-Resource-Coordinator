package com.disasterrelief.backend.dispatch;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

/** Request and response shapes for /api/dispatches (docs/API_CONTRACT.md section 5). */
public final class DispatchDtos {

    private DispatchDtos() {
    }

    /** Dispatch what an approved allocation reserved in one warehouse. */
    public record CreateRequest(@NotNull Long allocationId, @NotNull Long warehouseId,
                                @Size(max = 100) String vehicle, @Size(max = 150) String driverName,
                                @Size(max = 1000) String note) {
    }

    /** status must be IN_TRANSIT or DELIVERED. */
    public record StatusRequest(@NotNull String status, @Size(max = 1000) String note) {
    }

    public record TimelineEntry(String status, String note, String actorName, OffsetDateTime at) {
    }

    public record DispatchResponse(long id, long allocationId, long requestId, long warehouseId, String warehouseName,
                                   String resourceName, String unit, int quantity, String status, String vehicle,
                                   String driverName, String note, OffsetDateTime dispatchedAt,
                                   OffsetDateTime deliveredAt, List<TimelineEntry> timeline) {
    }
}
