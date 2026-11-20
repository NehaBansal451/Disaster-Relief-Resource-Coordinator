package com.disasterrelief.backend.system;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Safety net for production (Spring profile "prod"): the demo accounts share a published password
 * (see docs/INTEGRATION_GUIDE.md), so the server refuses to start if the "dev" profile is also active
 * or if any demo user already exists in the database. It runs before the web server accepts requests.
 */
@Component
@Profile("prod")
class DemoDataGuard implements SmartInitializingSingleton {

    private final JdbcTemplate jdbc;
    private final Environment environment;

    DemoDataGuard(JdbcTemplate jdbc, Environment environment) {
        this.jdbc = jdbc;
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        boolean devProfileActive = environment.acceptsProfiles(Profiles.of("dev"));
        Long demoUsers = jdbc.queryForObject("SELECT count(*) FROM users WHERE is_demo", Long.class);
        check(devProfileActive, demoUsers == null ? 0 : demoUsers);
    }

    static void check(boolean devProfileActive, long demoUsers) {
        if (devProfileActive) {
            throw new IllegalStateException(
                    "Refusing to start: the 'dev' profile (demo data) must not be active together with 'prod'.");
        }
        if (demoUsers > 0) {
            throw new IllegalStateException("Refusing to start: " + demoUsers
                    + " demo user(s) exist in the database. Demo accounts share a published password. "
                    + "Use a database that never had the 'dev' profile, or delete the demo rows first.");
        }
    }
}
