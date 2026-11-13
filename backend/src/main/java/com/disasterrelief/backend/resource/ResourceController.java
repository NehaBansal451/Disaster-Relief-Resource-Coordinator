package com.disasterrelief.backend.resource;

import com.disasterrelief.backend.security.StaffOnly;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/resources: active catalogue, read only, for the request form. Create/edit comes with the Day 5 inventory work. */
@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final JdbcClient jdbc;

    public ResourceController(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    @GetMapping
    @StaffOnly
    public List<ResourceItem> list() {
        return jdbc.sql("SELECT id, name, category, unit, perishable FROM resources WHERE active ORDER BY category, lower(name)")
                .query((rs, i) -> new ResourceItem(rs.getLong("id"), rs.getString("name"), rs.getString("category"),
                        rs.getString("unit"), rs.getBoolean("perishable")))
                .list();
    }
}
