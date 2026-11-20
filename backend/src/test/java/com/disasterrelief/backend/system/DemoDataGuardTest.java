package com.disasterrelief.backend.system;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DemoDataGuardTest {

    @Test
    void cleanProductionDatabaseIsAccepted() {
        assertDoesNotThrow(() -> DemoDataGuard.check(false, 0));
    }

    @Test
    void devProfileTogetherWithProdIsRefused() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> DemoDataGuard.check(true, 0));
        assertTrue(ex.getMessage().contains("'dev' profile"));
    }

    @Test
    void existingDemoUsersAreRefused() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> DemoDataGuard.check(false, 8));
        assertTrue(ex.getMessage().contains("8 demo user(s)"));
    }
}
