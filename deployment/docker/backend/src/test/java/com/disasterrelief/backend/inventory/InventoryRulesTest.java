package com.disasterrelief.backend.inventory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.disasterrelief.backend.common.ApiException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InventoryRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    @Test
    void availableIsOnHandMinusReserved() {
        assertEquals(70, InventoryRules.available(100, 30));
        assertEquals(0, InventoryRules.available(50, 50));
    }

    @Test
    void batchIsOptionalAndNormalized() {
        assertEquals(null, InventoryRules.normalizeBatch(null));
        assertEquals(null, InventoryRules.normalizeBatch("  "));
        assertEquals("LOT-7", InventoryRules.normalizeBatch(" lot-7 "));
    }

    @Test
    void expiryStatusBoundaries() {
        assertEquals("NONE", InventoryRules.expiryStatus(null, TODAY));
        assertEquals("EXPIRED", InventoryRules.expiryStatus(TODAY.minusDays(1), TODAY));
        assertEquals("EXPIRING_SOON", InventoryRules.expiryStatus(TODAY, TODAY));
        assertEquals("EXPIRING_SOON", InventoryRules.expiryStatus(TODAY.plusDays(30), TODAY));
        assertEquals("OK", InventoryRules.expiryStatus(TODAY.plusDays(31), TODAY));
    }

    @Test
    void perishableReceiptNeedsFutureExpiry() {
        assertThrows(ApiException.class, () -> InventoryRules.validateReceipt(true, null, TODAY));
        assertThrows(ApiException.class, () -> InventoryRules.validateReceipt(true, TODAY, TODAY));
        assertThrows(ApiException.class, () -> InventoryRules.validateReceipt(false, TODAY.minusDays(1), TODAY));
        assertDoesNotThrow(() -> InventoryRules.validateReceipt(true, TODAY.plusDays(1), TODAY));
        assertDoesNotThrow(() -> InventoryRules.validateReceipt(false, null, TODAY));
    }
}
