package com.disasterrelief.backend.warehouse;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/warehouses: read-only list with map coordinates. Create/edit is not in scope yet. */
@RestController
@RequestMapping("/api/warehouses")
public class WarehouseController {

    private final JdbcClient jdbc;

    public WarehouseController(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OFFICER','WAREHOUSE_MANAGER')")
    public List<WarehouseItem> list() {
        return jdbc.sql("""
                SELECT w.id, w.code, w.name, w.district, w.state, w.capacity_m3, w.status, w.is_demo,
                       ST_Y(w.location::geometry) AS latitude, ST_X(w.location::geometry) AS longitude, u.full_name
                FROM warehouses w LEFT JOIN users u ON u.id = w.manager_user_id
                ORDER BY lower(w.name)
                """)
                .query((rs, n) -> new WarehouseItem(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("district"), rs.getString("state"), rs.getBigDecimal("capacity_m3"),
                        rs.getString("status"), rs.getObject("latitude", Double.class),
                        rs.getObject("longitude", Double.class), rs.getString("full_name"), rs.getBoolean("is_demo")))
                .list();
    }
}
