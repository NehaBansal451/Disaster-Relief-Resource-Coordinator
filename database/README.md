# database/

PostgreSQL 16 + PostGIS. Flyway migrations are the single source of truth (status: PROPOSED, pending Divay's approval, decision D1).

| Path | Purpose |
|---|---|
| `migrations/V<n>__*.sql` | Versioned schema. Never edit a migration that has been applied anywhere; add a new version. |
| `seed/R__dev_seed.sql` | Fictional development/demo data (`is_demo = TRUE`), repeatable and idempotent. Loaded only with Spring profile `dev`. |
| `queries/day2_checks.sql` | Self-check: constraints reject bad data and spatial indexes exist. Changes nothing. |

The backend runs Flyway at startup: `backend/pom.xml` copies `migrations/` and `seed/` into the jar as `db/migration` and `db/seed`.

Run the database: `cd deployment/docker && cp .env.example .env && docker compose up -d`.
Manual check against a throwaway DB: `psql -v ON_ERROR_STOP=1 -d <db> -f database/queries/day2_checks.sql`.
Demo logins (profile `dev` only): `admin@demo.drrc.local`, `officer@demo.drrc.local`, ... password `Demo@12345`.
