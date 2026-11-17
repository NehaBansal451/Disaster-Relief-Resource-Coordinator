package com.disasterrelief.backend.inventory;

import java.time.LocalDate;

/**
 * One stock line (warehouse x resource x batch). available = onHand - reserved. expiryStatus: NONE, OK, EXPIRING_SOON
 * (within 30 days) or EXPIRED. Quantities always come with the unit.
 */
public record InventoryRow(long id, long warehouseId, String warehouseCode, String warehouseName,
                           long resourceId, String resourceName, String unit, String batchRef,
                           int onHand, int reserved, int available, LocalDate expiryDate, String expiryStatus) {
}
