package com.disasterrelief.backend.system;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Reports whether the backend can reach PostgreSQL/PostGIS and which migration version is applied. */
@Service
public class SystemStatusService {

    private final JdbcTemplate jdbcTemplate;
    private final String applicationName;

    public SystemStatusService(JdbcTemplate jdbcTemplate,
                               @Value("${spring.application.name}") String applicationName) {
        this.jdbcTemplate = jdbcTemplate;
        this.applicationName = applicationName;
    }

    public SystemStatusResponse currentStatus() {
        try {
            String postgis = jdbcTemplate.queryForObject("SELECT postgis_version()", String.class);
            String migration = jdbcTemplate.queryForObject(
                    "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL "
                            + "ORDER BY installed_rank DESC LIMIT 1", String.class);
            return new SystemStatusResponse(applicationName, "UP",
                    new SystemStatusResponse.Database("UP", postgis, migration));
        } catch (DataAccessException ex) {
            // Do not leak the exception text (it can contain connection details).
            return new SystemStatusResponse(applicationName, "DEGRADED",
                    new SystemStatusResponse.Database("DOWN", null, null));
        }
    }
}
