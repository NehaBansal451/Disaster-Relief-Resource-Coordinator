package com.disasterrelief.backend.analytics;

/** Pure formulas for the analytics screens, so they can be unit-tested. */
final class AnalyticsRules {

    private AnalyticsRules() {
    }

    static long shortage(long demand, long available) {
        return Math.max(0, demand - Math.max(0, available));
    }

    /** Share of demand that free stock covers, capped at 100. No demand counts as fully covered. */
    static double coveragePercent(long demand, long available) {
        if (demand <= 0) {
            return 100.0;
        }
        return Math.round(Math.min(100.0, Math.max(0, available) * 100.0 / demand) * 10.0) / 10.0;
    }

    static double fulfilmentPercent(long deliveredQuantity, long requestedQuantity) {
        if (requestedQuantity <= 0) {
            return 0.0;
        }
        return Math.round(Math.min(100.0, deliveredQuantity * 100.0 / requestedQuantity) * 10.0) / 10.0;
    }
}
