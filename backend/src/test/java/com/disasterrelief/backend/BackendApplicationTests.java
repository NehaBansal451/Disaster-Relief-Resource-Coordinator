package com.disasterrelief.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Full-context test: starts Spring, runs Flyway (including the dev seed) and validates the schema.
 * It needs a running PostgreSQL/PostGIS, so it is skipped (not failed) when DB_PASSWORD is not set.
 * Run it with: docker compose up -d (deployment/docker) and DB_PASSWORD exported.
 * Replacing this with Testcontainers is a later, separately approved step.
 */
@SpringBootTest
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
