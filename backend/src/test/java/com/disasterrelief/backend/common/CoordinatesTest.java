package com.disasterrelief.backend.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CoordinatesTest {

    @Test
    void acceptsBoundaryValues() {
        assertTrue(Coordinates.check(90.0, 180.0, true).isEmpty());
        assertTrue(Coordinates.check(-90.0, -180.0, true).isEmpty());
        assertTrue(Coordinates.check(0.0, 0.0, true).isEmpty());
    }

    @Test
    void rejectsJustOutsideBoundaries() {
        assertEquals(1, Coordinates.check(90.0001, 0.0, true).size());
        assertEquals(1, Coordinates.check(0.0, -180.0001, true).size());
    }

    @Test
    void optionalLocationMayBeAbsentButRequiredMayNot() {
        assertTrue(Coordinates.check(null, null, false).isEmpty());
        assertEquals(2, Coordinates.check(null, null, true).size());
    }

    @Test
    void halfAPairIsAlwaysAnError() {
        assertEquals(1, Coordinates.check(30.0, null, false).size());
        assertEquals(1, Coordinates.check(null, 76.0, false).size());
    }

    @Test
    void rejectsNaN() {
        assertEquals(1, Coordinates.check(Double.NaN, 76.0, false).size());
    }
}
