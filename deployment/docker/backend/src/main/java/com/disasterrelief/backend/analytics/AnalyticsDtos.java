package com.disasterrelief.backend.analytics;

import java.util.Map;

public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    /** fulfilmentPercent = delivered quantity / quantity of all requests that were not rejected or cancelled. */
    public record Summary(Long disasterId, long totalRequests, Map<String, Long> byStatus, Map<String, Long> bySeverity,
                          long awaitingVerification, long openCritical, long deliveredQuantity, long requestedQuantity,
                          double fulfilmentPercent, Double averageHoursToDeliver) {
    }

    /** Unallocated demand (VERIFIED or APPROVED requests) against free stock in ACTIVE warehouses. */
    public record ShortageRow(long resourceId, String resource, String unit, long openRequests, long demand,
                              long available, long shortage, double coveragePercent, long offeredByPartners) {
    }
}
