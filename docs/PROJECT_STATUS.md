# Project Status

Last updated: 13 Nov 2026 (Day 3 merged to `main`; Day 4 on `feature/day4-requests`; see "Day 3 status" and "Day 4 status" at the end). Day 1-2 approvals below are still PENDING: only Neha and Divay can record them.

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

## Day 3 status (11 Nov 2026) - implemented on `feature/day3-auth-disasters`, NOT merged, NOT built by Maven
Scope: authentication, roles, disasters, affected areas (backend + Vaadin screens).

### Backend (`backend/`)
- Spring Security, stateless JWT (HS256), BCrypt. `POST /api/auth/login`, `GET /api/auth/me`. Wrong password, unknown email and disabled account return the same 401 message.
- Server-side roles via `@PreAuthorize`: `@AdminOrOfficer` (create/update) and `@StaffOnly` (read). `GET /api/system/status` is now ADMIN-only (public liveness stays at `/actuator/health`).
- `GET/POST/PUT /api/disasters`, `GET /api/disasters/{id}`, `GET/POST /api/affected-areas` with validation, paging (`page`, `size` max 100, whitelisted `sort`), filters, and one error format (RFC 9457 problem JSON + `code`).
- Disasters and areas use SQL (`JdbcClient`) because of the PostGIS geography columns; Hibernate Spatial is still not added (deliberate).
- `JWT_SECRET` is mandatory and has no default; the app refuses to start with a missing or short secret.

### Frontend (`frontend/`)
- Login page, route guard (everything except `/login` needs a login), role-aware menu, disaster list (filters, paging), create-disaster form, disaster detail with affected-area table and add-area dialog, admin-only status page (moved to `/status`). The token lives only in the server-side Vaadin session.

### What was actually verified (sandbox: PostgreSQL 16 + PostGIS 3.4)
- V1..V4 + seed apply cleanly; `day2_checks.sql` 22 of 22 pass; seed hash verifies against `Demo@12345`.
- The exact SQL shapes used by the new repositories were run against PostGIS: insert with and without coordinates, filtered ranked paged read, update, the `CLOSED needs ended_at` constraint, the duplicate-area constraint (also with NULL district), unfiltered read.
- Maven XML well-formed; `javac` found no syntax errors in any file (only errors caused by missing libraries).

### NOT verified (must be run by the team)
- `./mvnw test` in `backend/` and `frontend/` (Maven Central is blocked in the sandbox, so no Java was compiled or run). All new unit tests and `ApiSecurityIntegrationTest` are unrun.
- Real login/create-disaster flow in a browser; Vaadin rendering.
- Spring Boot 4 / Spring Security 7 / Vaadin 25 API names used from knowledge, not compiled.

### Run it (after merging the database branch)
1. `cd deployment/docker && cp .env.example .env`, set `DB_PASSWORD` and `JWT_SECRET`, `docker compose up -d`.
2. Export the `.env` values, then `cd backend && ./mvnw spring-boot:run` (profile `dev` loads the demo seed).
3. `cd frontend && ./mvnw spring-boot:run`, open http://localhost:8081, log in as `officer@demo.drrc.local` / `Demo@12345`.
4. `cd backend && DB_PASSWORD=... ./mvnw test` runs the integration test too (without `DB_PASSWORD` it is skipped).

### Assumptions made in Day 3 (need approval)
| # | Assumption |
|---|---|
| T1 | "staff" (read access) = ADMIN, OFFICER, WAREHOUSE_MANAGER, FIELD_WORKER, NGO; VOLUNTEER and CITIZEN get 403 on disaster data. The contract does not define "staff". |
| T2 | Only ADMIN and OFFICER create/update disasters and areas. |
| T3 | Areas cannot be added to a CLOSED disaster (409 `INVALID_STATE`). |
| T4 | `endedAt` allowed only when status is CLOSED, and required then (mirrors the DB constraint). |
| T5 | Duplicate area name + district within a disaster returns 409 `DUPLICATE_AREA`. |
| T6 | `/api/system/status` is ADMIN only. |
| T7 | UI times are entered and shown in Asia/Kolkata. |
| T8 | No refresh tokens; 1 hour access token (decision A6). |

### Known follow-ups
- Login rate limiting / lockout not implemented.
- No session-id rotation on login in the Vaadin app.
- No audit log of disaster changes yet (planned later).
- Frontend has no end-to-end browser test.

## Day 3 update (merged)
Day 3 (auth, roles, disasters, affected areas, Vaadin login/list/create/detail) is merged to `main` (PRs #8, #9, plus the `vaadin-dev` fix). Backend `mvnw test`: BUILD SUCCESS. Frontend `mvnw test`: 18 tests pass. Manual run on Windows: login, disaster list, create disaster, add affected area all worked. Local ports: `DB_PORT=15432` because Windows blocked 5432 (`INTEGRATION_GUIDE.md`). Not yet run by anyone: `ApiSecurityIntegrationTest` (needs `DB_PASSWORD` in the environment). UI theme (Lumo) is not enabled yet: cosmetic, deferred.

## Day 4 status (13 Nov 2026) - branch `feature/day4-requests`, NOT merged, NOT approved
Checkpoint: "a request submitted in the UI is saved and retrieved from PostgreSQL".

| Area | Owner | Delivered |
|---|---|---|
| Database | Divay (proposed by Neha) | `V5__need_requests.sql`: `need_requests`, `request_history`, composite FK so an area must belong to the request's disaster, reason/approval CHECKs, indexes; 3 demo requests in the dev seed; `queries/day4_checks.sql` |
| Backend | Neha | Package `request`: `GET/POST /api/requests`, `GET /api/requests/{id}` (with history), `PATCH .../verify`, `.../approve`, `.../reject`, `.../cancel` per `API_CONTRACT.md` section 1; package `resource`: `GET /api/resources`; history row on every change; race-safe conditional status updates |
| Frontend | Neha | Requests list (filters, paging), New request form (disaster -> area -> resource), request detail with history and role-aware Verify / Approve / Ask for clarification / Reject / Cancel |
| Docs | shared | `PROJECT_BLUEPRINT.md`, `DATABASE_DESIGN.md`, `INTEGRATION_GUIDE.md`, `DEMO_SCRIPT.md` |

### Verified in the sandbox (PostgreSQL 16 + PostGIS 3.4)
- V1-V5 and the seed applied in order; seed re-run inserts 0 new rows (idempotent).
- `day4_checks.sql`: 18 of 18 PASS (zero/negative quantity, blank/over-long description, bad severity/status, area from another disaster, unknown resource, reject/cancel without reason, approve without approver, score > 100, bad history action, RESTRICT delete). `day2_checks.sql`: still 22 of 22.
- The repository SELECT/JOIN, history and conditional UPDATE statements run correctly on that database.

### NOT verified (Maven Central is blocked in the sandbox; must be run locally)
- `mvnw test` for backend and frontend (new: `RequestRulesTest`, `RequestServiceTest`, `RequestApiIntegrationTest`, `RequestClientTest`, `RequestActionsTest`). Java was written carefully but has not been compiled here.
- The Vaadin screens have not been seen in a browser.

### Assumptions added by Day 4 (need approval)
| # | Assumption |
|---|---|
| R1 | Submit: OFFICER, FIELD_WORKER, NGO only. CITIZEN excluded until O4 is decided. |
| R2 | Verify, approve, reject: OFFICER only (ADMIN excluded, O1 default). |
| R3 | Cancel: officer any of PENDING/VERIFIED/APPROVED; requester only own PENDING. ALLOCATED and later cannot be cancelled yet (O3). |
| R4 | REQUEST_CLARIFICATION is a history event only; status stays PENDING. |
| R5 | `ADD` `affected_areas UNIQUE (id, disaster_id)` in V5 to enforce area/disaster consistency in the database (Divay to confirm). |
| R6 | `GET /api/resources` added (read only) because the request form needs a catalogue; not in the original contract list. |
| R7 | Priority fields are null and `priorityReasons` empty until Day 6. |
| R8 | Approval note is accepted but not stored separately in Day 4 (stored as `status_reason`). |

### Next: Day 5
Inventory and shelter tables (Divay), inventory APIs/UI, shelters, dashboard queries (Neha). Prerequisite: Divay answers D1-D6 and approves V5.

