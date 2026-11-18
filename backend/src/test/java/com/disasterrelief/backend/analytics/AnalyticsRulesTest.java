package com.disasterrelief.backend.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AnalyticsRulesTest {

    @Test
    void shortageIsDemandMinusFreeStockAndNeverNegative() {
        assertEquals(40, AnalyticsRules.shortage(100, 60));
        assertEquals(0, AnalyticsRules.shortage(50, 80));
        assertEquals(100, AnalyticsRules.shortage(100, -5));
    }

    @Test
    void coverageIsCappedAndNoDemandCountsAsCovered() {
        assertEquals(60.0, AnalyticsRules.coveragePercent(100, 60));
        assertEquals(100.0, AnalyticsRules.coveragePercent(50, 500));
        assertEquals(100.0, AnalyticsRules.coveragePercent(0, 0));
        assertEquals(0.0, AnalyticsRules.coveragePercent(10, -3));
        assertEquals(33.3, AnalyticsRules.coveragePercent(3, 1));
    }

    @Test
    void fulfilmentIsDeliveredOverRequested() {
        assertEquals(25.0, AnalyticsRules.fulfilmentPercent(25, 100));
        assertEquals(100.0, AnalyticsRules.fulfilmentPercent(150, 100));
        assertEquals(0.0, AnalyticsRules.fulfilmentPercent(5, 0));
    }
}
