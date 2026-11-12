package com.disasterrelief.backend.disaster;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.disasterrelief.backend.common.ApiException;
import com.disasterrelief.backend.common.Severity;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class DisasterRulesTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-11-03T06:00:00+05:30");

    private static DisasterRequest request(DisasterStatus status, OffsetDateTime ended, Double lat, Double lon) {
        return new DisasterRequest("Flood", DisasterType.FLOOD, Severity.HIGH, status, START, ended, null, lat, lon);
    }

    @Test
    void statusDefaultsToActive() {
        assertEquals(DisasterStatus.ACTIVE, DisasterRules.effectiveStatus(request(null, null, null, null)));
    }

    @Test
    void validActiveDisasterWithoutLocationPasses() {
        assertDoesNotThrow(() -> DisasterRules.validate(request(null, null, null, null)));
    }

    @Test
    void closedRequiresEndDate() {
        ApiException ex = assertThrows(ApiException.class,
                () -> DisasterRules.validate(request(DisasterStatus.CLOSED, null, null, null)));
        assertEquals("VALIDATION_ERROR", ex.code());
        assertEquals("endedAt", ex.errors().get(0).field());
    }

    @Test
    void endDateOnlyAllowedWhenClosed() {
        assertThrows(ApiException.class,
                () -> DisasterRules.validate(request(DisasterStatus.ACTIVE, START.plusDays(1), null, null)));
        assertDoesNotThrow(() -> DisasterRules.validate(request(DisasterStatus.CLOSED, START.plusDays(1), null, null)));
    }

    @Test
    void endDateEqualToStartIsAllowedButBeforeStartIsNot() {
        assertDoesNotThrow(() -> DisasterRules.validate(request(DisasterStatus.CLOSED, START, null, null)));
        assertThrows(ApiException.class,
                () -> DisasterRules.validate(request(DisasterStatus.CLOSED, START.minusSeconds(1), null, null)));
    }

    @Test
    void coordinatesMustComeInPairsAndInRange() {
        assertThrows(ApiException.class, () -> DisasterRules.validate(request(null, null, 30.0, null)));
        assertThrows(ApiException.class, () -> DisasterRules.validate(request(null, null, 91.0, 76.0)));
        assertDoesNotThrow(() -> DisasterRules.validate(request(null, null, 30.28, 76.10)));
    }

    @Test
    void collectsEveryProblemInOneResponse() {
        ApiException ex = assertThrows(ApiException.class,
                () -> DisasterRules.validate(request(DisasterStatus.CLOSED, null, 95.0, null)));
        assertEquals(3, ex.errors().size());
    }
}
