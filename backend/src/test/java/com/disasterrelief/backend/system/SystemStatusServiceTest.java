package com.disasterrelief.backend.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class SystemStatusServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void reportsUpWithPostgisAndMigrationVersionWhenDatabaseAnswers() {
        when(jdbcTemplate.queryForObject(eq("SELECT postgis_version()"), eq(String.class)))
                .thenReturn("3.4 USE_GEOS=1");
        when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.contains("flyway_schema_history"), eq(String.class)))
                .thenReturn("4");

        SystemStatusResponse response = new SystemStatusService(jdbcTemplate, "backend").currentStatus();

        assertEquals("UP", response.status());
        assertEquals("UP", response.database().status());
        assertEquals("3.4 USE_GEOS=1", response.database().postgis());
        assertEquals("4", response.database().latestMigration());
    }

    @Test
    void reportsDegradedWithoutLeakingDetailsWhenDatabaseFails() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class)))
                .thenThrow(new DataAccessResourceFailureException("jdbc:postgresql://secret-host:5432 refused"));

        SystemStatusResponse response = new SystemStatusService(jdbcTemplate, "backend").currentStatus();

        assertEquals("DEGRADED", response.status());
        assertEquals("DOWN", response.database().status());
        assertNull(response.database().postgis());
        assertNull(response.database().latestMigration());
    }
}
