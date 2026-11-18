package com.disasterrelief.backend.shelter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.disasterrelief.backend.common.ApiException;
import org.junit.jupiter.api.Test;

class ShelterRulesTest {

    private static ShelterRequest request(String status, Double lat, Double lon) {
        return new ShelterRequest(null, "School", null, null, null, 100, 0, null, false, status, lat, lon);
    }

    @Test
    void overcrowdedOnlyWhenOccupancyExceedsCapacity() {
        assertFalse(ShelterRules.overcrowded(100, 100));
        assertTrue(ShelterRules.overcrowded(100, 101));
    }

    @Test
    void freeBedsNeverNegative() {
        assertEquals(20, ShelterRules.freeBeds(100, 80));
        assertEquals(0, ShelterRules.freeBeds(100, 140));
    }

    @Test
    void statusDefaultsToOpenAndIsChecked() {
        assertEquals("OPEN", ShelterRules.effectiveStatus(null));
        assertEquals("CLOSED", ShelterRules.effectiveStatus(" closed "));
        assertThrows(ApiException.class, () -> ShelterRules.validate(request("FULL", 30.0, 76.0)));
    }

    @Test
    void coordinatesAreRequiredAndRangeChecked() {
        ShelterRules.validate(request("OPEN", 30.3, 76.4));
        assertThrows(ApiException.class, () -> ShelterRules.validate(request("OPEN", 95.0, 76.4)));
        assertThrows(ApiException.class, () -> ShelterRules.validate(request("OPEN", null, 76.4)));
    }
}
