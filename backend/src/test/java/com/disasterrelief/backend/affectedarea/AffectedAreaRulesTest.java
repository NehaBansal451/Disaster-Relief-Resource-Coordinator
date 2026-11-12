package com.disasterrelief.backend.affectedarea;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.disasterrelief.backend.common.ApiException;
import com.disasterrelief.backend.common.Severity;
import org.junit.jupiter.api.Test;

class AffectedAreaRulesTest {

    private static AffectedAreaRequest request(AreaType type, Double lat, Double lon) {
        return new AffectedAreaRequest(1L, "Demo Village", type, "Patiala", "Punjab", 1200, Severity.HIGH, null, lat, lon);
    }

    @Test
    void areaTypeDefaultsToVillage() {
        assertEquals(AreaType.VILLAGE, AffectedAreaRules.effectiveType(request(null, 30.0, 76.0)));
        assertEquals(AreaType.TOWN, AffectedAreaRules.effectiveType(request(AreaType.TOWN, 30.0, 76.0)));
    }

    @Test
    void locationIsMandatory() {
        ApiException ex = assertThrows(ApiException.class, () -> AffectedAreaRules.validate(request(null, null, null)));
        assertEquals(2, ex.errors().size());
    }

    @Test
    void validLocationPasses() {
        assertDoesNotThrow(() -> AffectedAreaRules.validate(request(null, 30.285, 76.15)));
    }

    @Test
    void outOfRangeLocationIsRejected() {
        assertThrows(ApiException.class, () -> AffectedAreaRules.validate(request(null, 30.0, 181.0)));
    }
}
