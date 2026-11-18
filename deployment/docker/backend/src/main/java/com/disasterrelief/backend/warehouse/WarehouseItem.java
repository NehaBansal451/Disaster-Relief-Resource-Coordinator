package com.disasterrelief.backend.warehouse;

import java.math.BigDecimal;

public record WarehouseItem(long id, String code, String name, String district, String state, BigDecimal capacityM3,
                            String status, Double latitude, Double longitude, String managerName, boolean demo) {
}
