package com.disasterrelief.backend.dashboard;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** GET /api/dashboard. Every figure is computed by SQL at request time; nothing here is stored or hard-coded. */
public record DashboardResponse(
        long activeDisasters,
        long affectedPopulation,
        Map<String, Long> requestsByStatus,
        Map<String, Long> requestsBySeverity,
        long openCriticalRequests,
        List<StockSummary> stock,
        ShelterSummary shelters,
        List<Activity> recentActivity) {

    /** Stock of one resource across all warehouses. expiringQuantity = unexpired stock expiring within 30 days. */
    public record StockSummary(long resourceId, String resourceName, String unit, long onHand, long reserved,
                               long available, long expiringQuantity, long expiredQuantity) {
    }

    public record ShelterSummary(long openShelters, long totalCapacity, long totalOccupancy, long overcrowdedShelters) {
    }

    public record Activity(long requestId, String action, String toStatus, String actorName, String reason,
                           OffsetDateTime at) {
    }
}
