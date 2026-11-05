# Project Status

Last updated: 5 Nov 2026 (Day 1 review corrections). No application code written; no existing repository file modified; nothing built or tested.

## Day 1 review outcome
- Direction approved in principle. Docs v0.2 corrected as requested; approvals below still pending.
- Day 2 coding NOT started.

## Approval register
| Item | Neha | Divay |
|---|---|---|
| Priority weights (0.40/0.30/0.15/0.15) - PROPOSED | pending | pending |
| Priority thresholds (80/60/40) - PROPOSED | pending | pending |
| Request approval endpoint and state machine (`API_CONTRACT.md` 1-2) | pending | pending |
| Vaadin/Node.js build-tooling policy (`SYSTEM_ARCHITECTURE.md` 7) | pending | pending |
| D1 Flyway ownership/execution | proposed | **needs Divay** |
| D2 Migration V2 | proposed | **needs Divay** |
| D3 Database ER design | proposed | **needs Divay** |
| D4 Inventory constraints | proposed | **needs Divay** |
| D5 Demo data | proposed | **needs Divay** |
| D6 Deployment architecture | proposed | **needs Divay** |

## Remaining decisions
O1 ADMIN approving requests | O2 self-approval ban | O3 cancel after ALLOCATED / dispatch cancel / stock release | O4 public citizen reporting | O5 priority recalculation over time | O6 residual quantity after partial allocation | O7 same officer verifies and approves | package name `com.disasterrelief.backend` (A3) | tracking `frontend/package.json` | Boot 4 / Vaadin 25 compatibility confirmed only on Day 2.

## Day 2 readiness checklist (all must be ticked before Day 2 coding)
- [ ] Neha and Divay reviewed `SYSTEM_ARCHITECTURE.md` v0.2, `API_CONTRACT.md` v0.2, `TEAM_RESPONSIBILITIES.md`
- [ ] Docs committed via PR to `main` (branch `docs/day1-architecture`)
- [ ] D1 decided (who runs Flyway, how migrations reach the backend)
- [ ] D2 migration V2 content agreed (at minimum `FIELD_WORKER`) or consciously deferred
- [ ] D3 ER draft accepted as baseline for Days 3-5 tables
- [ ] D4 inventory constraint approach accepted (needed by Day 6, baseline now)
- [ ] D5 and D6 may wait, but owner and target day recorded
- [ ] Priority weights/thresholds remain "proposed" (not blocking Day 2)
- [ ] Node.js policy approved and a Node version available on both machines
- [ ] Divay confirms `docker compose up` works and V1 applies on a clean DB
- [ ] Neha confirms `./mvnw test` passes on the current skeleton locally (not yet run by anyone)
- [ ] Ports, env var names and run commands agreed (8080/8081, `DB_*`, `JWT_SECRET`, `DRRC_BACKEND_URL`)

## Day 2 scope (for when approved; not started)
Dependency order: (1) approvals above -> (2) V2 / Flyway wiring [Divay] -> (3) backend dependencies and Testcontainers [Neha] -> (4) Vaadin 25 skeleton + login shell [Neha] -> (5) compose/CI skeleton [Divay] -> (6) connectivity checkpoint.

## Risks
| Risk | Mitigation |
|---|---|
| Vaadin 25 build requires Node.js; conflicts with literal reading of "no Node" | Build-tooling-only policy awaiting approval |
| Boot 4 / Jackson 3 / Vaadin 25 are new | Pin versions; smoke test on Day 2 |
| Ambitious 10-day plan | Core chain first; defer features 15, 17, 20, 21 if needed |
| Stock race conditions | D4 + Day 6 concurrency test |
| Hosting limits unknown | Divay verifies before Day 8 |
| Map component licence unverified | Evaluate before Day 7 |
| Claude API cost/privacy | Document transmitted fields, rate limit, fallback |

## Day 2 status (5 Nov 2026) - implemented on branches, NOT merged, NOT approved
Work was prepared in three separate branches so ownership stays clean. Nothing below is an approval: the register above is unchanged, and Divay's formal review of D1-D6 is not recorded.

| Branch | Owner of files | Contents |
|---|---|---|
| `feature/day2-backend-frontend` | Neha | `backend/`: JPA, PostgreSQL driver, Flyway starter, Maven resources mapping `../database/*`, config via env vars, `GET /api/system/status`, unit tests. `frontend/`: Vaadin 25 project, REST client, status page, unit tests. |
| `feature/day2-database` | Divay (prepared for his review) | `database/migrations/V2-V4`, `database/seed/R__dev_seed.sql`, `database/queries/day2_checks.sql`, `database/README.md`; `deployment/docker/`: DB port bound to loopback, extended `.env.example`. |
| `docs/day2-status` | shared | This section only. |
Merge order: database branch first, then backend/frontend (the backend build copies `../database/migrations`).

### What was actually verified (sandbox: PostgreSQL 16.15 + PostGIS 3.4 installed natively, no Docker)
- V1..V4 applied in order to empty databases with `psql -v ON_ERROR_STOP=1`: all succeeded.
- `database/queries/day2_checks.sql`: 22 of 22 checks passed on an empty database and on a seeded one (invalid enum values, duplicate emails ignoring case, bad dates, negative population, FK and RESTRICT behaviour, resource shelf-life rule, duplicate warehouse code, spatial indexes, PostGIS).
- Seed loaded twice: first run 3 organisations, 8 users, 2 disasters, 3 affected areas, 8 resources, 3 warehouses; second run inserted 0 rows (idempotent).
- Both `pom.xml` files are well-formed XML; `docker-compose.yml` parses as YAML.
- `javac` 21 on all 12 Java files: 151 errors, all caused by missing libraries (JUnit, Spring, Vaadin, Mockito); no error in our own code. This is a weak syntax-level check only.

### NOT verified (must be run by the team)
- `./mvnw test` / `mvn compile` for backend and frontend: Maven could not download anything. Exact error: `Non-resolvable parent POM ... Could not transfer artifact org.springframework.boot:spring-boot-starter-parent:pom:4.1.2 from/to central (https://repo.maven.apache.org/maven2)` (sandbox has no access to Maven Central).
- Real Flyway execution (Flyway CLI unobtainable in the sandbox); the SQL was applied with `psql` in Flyway order instead.
- `docker compose up` (Docker not installed in the sandbox), backend and frontend startup, `GET /api/system/status`, the Vaadin page, and all Java unit tests.
- Unconfirmed names/versions: `spring-boot-starter-flyway`, `flyway-database-postgresql` under Boot 4.1.2, and `vaadin.version` 25.3.4 (change to the newest 25.x if resolution fails).

### Open points from Day 2
- Resource catalogue is seeded as dev data without `is_demo`; is it reference data that should be a versioned migration? (Divay)
- `warehouses.capacity_m3` unit is a proposal. (Divay)
- Docs on `main` are still the pre-correction Day 1 v0.2 (A16 wording, ownership banners); the corrected versions have not been committed.
- Hibernate Spatial dependency and JPA entities are intentionally deferred to Day 3.
