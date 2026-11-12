package com.disasterrelief.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Full-context test: starts Spring, runs Flyway (including the dev seed) and validates the schema.
 * It needs a running PostgreSQL/PostGIS, so it is skipped (not failed) when DB_PASSWORD is not set.
 * Run it with: docker compose up -d (deployment/docker) and DB_PASSWORD exported.
 * Replacing this with Testcontainers is a later, separately approved step.
 */
@SpringBootTest
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
// Test-only signing key so the context starts without exporting JWT_SECRET. Not a real secret.
@TestPropertySource(properties = "drrc.security.jwt-secret=context-test-only-secret-0123456789abcdef")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
