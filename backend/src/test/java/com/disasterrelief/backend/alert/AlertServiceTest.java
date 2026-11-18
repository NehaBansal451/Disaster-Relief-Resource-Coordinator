package com.disasterrelief.backend.alert;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AlertServiceTest {

    @Test
    void lowStockWhenTenPercentOrLessIsAvailable() {
        assertTrue(AlertService.isLowStock(100, 90));
        assertTrue(AlertService.isLowStock(100, 100));
        assertFalse(AlertService.isLowStock(100, 89));
        assertFalse(AlertService.isLowStock(0, 0));
    }
}
