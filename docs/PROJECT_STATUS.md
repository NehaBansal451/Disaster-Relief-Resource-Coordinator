# Project Status

Last updated: 20 Nov 2026. The sections from "Day 1 review outcome" down are a dated log, kept as history; the block below is the current picture.

## Current status (20 Nov 2026)
- **Scope:** 15 features, all built (dashboard, map, inventory, need reporting, shelters, priority score, allocation, shortage detection, delivery tracking, report analyzer, volunteers, partners, PDF report, audit, alerts). Six features were removed from scope on 20 Nov 2026 (see `PROJECT_BLUEPRINT.md`).
- **Code on `main`:** backend and frontend for all 15 features, 11 database migrations plus demo seed data.
- **Automated checks:** GitHub Actions (`.github/workflows/ci.yml`) builds and tests backend (with a PostGIS database) and frontend on every push; `codeql.yml` scans the Java code weekly; `dependabot.yml` watches dependencies. Look at the repository's Actions tab for the latest result; do not rely on this file for it.
- **Production safety:** profile `prod` never loads demo data and refuses to start if demo users exist (`DemoDataGuard`).
- **Still open:** a rehearsed end-to-end demo (`DEMO_SCRIPT.md`, rehearsal log), approvals (`APPROVALS.md`), deployment and hosting (Divay, decision D6), and the accepted limits in `SECURITY_REVIEW.md`.
- **Approvals:** none recorded. Use `APPROVALS.md` to decide, then update the register below.

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
| Ambitious 10-day plan | Core chain first; the 15 features in the blueprint catalogue are the full scope |
| Stock race conditions | D4 + Day 6 concurrency test |
| Hosting limits unknown | Divay verifies before Day 8 |
| Map component licence unverified | Evaluate before Day 7 |
| AI API cost/privacy | Document transmitted fields, rate limit, fallback |

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

## Day 5 status (16 Nov 2026) - backend and database only, branch `feature/day5-inventory`, NOT merged, NOT approved
Scope delivered this round: database and backend. Inventory UI, shelter views and the dashboard screen (frontend) are NOT built yet.

| Area | Delivered |
|---|---|
| Database (proposed for Divay) | `V6__inventory_shelters.sql`: `inventory` (CHECK reserved <= on_hand), append-only `inventory_transactions` ledger (trigger blocks UPDATE/DELETE), `shelters` (spatial index); `R__dev_seed_day5.sql` (10 stock lines, opening ledger rows, 3 shelters incl. one overcrowded); `queries/day5_checks.sql` |
| Backend | `GET /api/inventory` (filters warehouse/resource/expiringOnly, paging, sort), `GET /api/inventory/{id}/transactions`, `POST /api/inventory/receipts`, `GET /api/warehouses`, shelters (`GET` list/detail/alternatives, `POST`, `PATCH .../occupancy`), `GET /api/dashboard` |

### Verified in the sandbox (PostgreSQL 16 + PostGIS 3.4)
V1-V6 and both seed files applied; seed run twice inserts nothing new; `day5_checks.sql` 22 of 22 PASS, `day4_checks.sql` 18 of 18, `day2_checks.sql` 22 of 22. The inventory, dashboard, nearest-shelter and receipt-upsert SQL ran correctly on that database.

### NOT verified (must be run locally)
`mvnw test` for the new Java code (`InventoryRulesTest`, `InventoryServiceTest`, `ShelterRulesTest`, `DashboardApiIntegrationTest`): Maven Central is blocked in the sandbox.

### Assumptions added by Day 5 (need approval)
| # | Assumption |
|---|---|
| S1 | Stock receipts: ADMIN anywhere; WAREHOUSE_MANAGER only into the warehouse they manage. OFFICER can read stock but not receive. |
| S2 | Shelter create: ADMIN, OFFICER. Occupancy update: ADMIN, OFFICER, FIELD_WORKER. Occupancy may exceed capacity (that is "overcrowded"). |
| S3 | available = on_hand - reserved. Reserved stock stays on hand until dispatched (Day 7 deducts it with a DISPATCH ledger row). |
| S4 | Perishable resources need a future expiry date on receipt; a batch cannot be re-received with a different expiry (409 `BATCH_EXPIRY_MISMATCH`). "Expiring soon" = within 30 days. |
| S5 | The ledger is append-only; corrections will be ADJUSTMENT rows (Day 6+). |
| S6 | `GET /api/warehouses` and `GET /api/dashboard` added; dashboard is open to all staff roles. |
| S7 | Shelter alternatives = 5 nearest other OPEN shelters with free beds. |

### Next: Day 6 (priority score, allocation, approval, concurrency test)


## Day 6–7 status (priority, allocation, dispatch, audit, alerts, map)

**Built:** V7 (priority + allocations) and V8 (dispatches, delivery updates, audit_logs, alerts) migrations; priority scoring (explainable, deterministic, officer override with reason); allocation recommend/approve/reject with FEFO reservation, fairness cap, stale-version protection and row-lock concurrency safety; dispatch → in transit → delivered with stock deduction and ledger; audit trail written in the same transaction; alerts (critical request, low stock, overcrowded shelter, approved, dispatched, delivered); `/api/map` data.

**Verified:** migrations V1–V8 apply on PostGIS; DB rules checked (one live allocation per request, reserved ≤ on-hand, audit append-only). Day 5 backend starts and runs on the user's machine.

**Not yet verified:** Day 6–7 Java compile and tests on the user's machine (sandbox cannot reach Maven Central). Integration tests `AllocationDispatchIntegrationTest` need DB_PASSWORD set.

**Proposed assumptions (need approval):** only OFFICER may recommend/approve/reject allocations (ADMIN excluded); competing demand = other VERIFIED/APPROVED requests for the same resource; fairness = proportional cap; low stock = available ≤ 10% of on-hand; priority weights 0.40 severity / 0.30 population / 0.15 waiting time / 0.15 resource criticality, caps 5000 people / 72 h, category thresholds 80/60/40; all staff see all alerts.

**New endpoints:** POST /api/requests/{id}/priority, PATCH /api/requests/{id}/priority-override; POST /api/allocations/recommendations, POST /api/allocations/{id}/approve|reject, GET /api/allocations/{id}, GET /api/allocations?requestId=; GET/POST /api/dispatches, PATCH /api/dispatches/{id}/status; GET /api/audit; GET /api/alerts, PATCH /api/alerts/{id}/read; GET /api/map.

## Rebuild on Divay's schema + Day 8 status (volunteers, partners, report analyzer)

**Done:** Days 5–7 now use Divay's V6–V8 tables (`batch_number`, delta-based `inventory_transactions`, `shelters.capacity_people/current_occupancy`). My migrations are V9 (priority/allocation) and V10 (dispatch/audit/alerts). Day 8 adds V11 and three modules.

**Day 8 endpoints:** POST/GET `/api/volunteers`, GET `/api/volunteers/{id}`, PATCH `/api/volunteers/{id}/availability`, POST/GET `/api/volunteers/{id}/assignments`, PATCH `/api/assignments/{id}/status`; POST/GET `/api/partner-contributions`, PATCH `/api/partner-contributions/{id}/status`; POST/GET `/api/ai/analyses`, GET `/api/ai/analyses/{id}`, PATCH `/api/ai/analyses/{id}/review`.

**Analyzer safety rules (all in code and tested):** personal identifiers (phone, e-mail, Aadhaar-style and PAN numbers) are scrubbed before the text is stored or sent; the report is passed as data between markers; the answer must match a strict schema or it is rejected (HTTP 502); a missing key or an outage gives HTTP 503 and a stored FAILED record; 10 analyses per user per hour (HTTP 429); a result stays UNREVIEWED until an officer accepts or rejects it, and it never creates or changes a request. Names and addresses written in prose cannot be removed automatically, so staff must not paste them.

**Verified:** V1–V11 apply on a clean PostGIS database built from `main`; seven database checks pass (`database/queries/day7_checks.sql`); the pure Day 8 logic (JSON reader/writer, scrubber, schema parser) compiles and passes its checks under plain Java. **Not yet verified:** the full Maven test run on the rebuilt branch (the maintainer runs it locally).

**Assumptions needing approval:** V1 (ADMIN/OFFICER/NGO register and list volunteers; only ADMIN/OFFICER assign; a VOLUNTEER sees and updates only their own record), P1 (an NGO offers for its own organization; ADMIN/OFFICER decide; RECEIVED does not add stock, the warehouse manager still records it), AI1 (any staff role may analyze; only ADMIN/OFFICER review; limit 10 per hour; model set via `AI_MODEL` and key via the `AI_API_KEY` environment variable).

## Day 9–10 status (analytics, shortages, PDF report, login throttle, end-to-end test, release notes)

**Built:** `GET /api/analytics/summary` and `/api/analytics/shortages` (ADMIN/OFFICER; optional `disasterId`; real counts from the tables, nothing estimated); `GET /api/reports/situation.pdf` (A4 PDF with PDFBox 3.0.3, ADMIN/OFFICER, writes a `REPORT_GENERATED` audit row); login throttle (5 failed sign-ins per e-mail or 30 per address in 15 minutes gives HTTP 429 `TOO_MANY_ATTEMPTS`; in memory, one server instance); `docs/SECURITY_REVIEW.md`; `docs/RELEASE_NOTES.md`; `docs/API_CONTRACT.md` section 8 (all Day 5-10 endpoints).

**Tests added:** `AnalyticsRulesTest`, `LoginThrottleTest`, `PdfSituationReportTest`, and `e2e/Day8To10ApiIntegrationTest` (volunteers, partner offers, analytics, PDF, throttle, analyzer with no key). Together with `AllocationDispatchIntegrationTest` (request -> priority -> allocation -> dispatch -> delivered, plus the two-officer race) this is the end-to-end proof. Integration tests need `DB_PASSWORD` set and a running database.

**Verified here:** V1-V11 apply on a clean PostGIS database from `main`; the analytics SQL returns correct rows on the demo data. **Not verified here:** Maven compile and test of Days 8-10 (sandbox cannot reach Maven Central); the maintainer must run `.\mvnw.cmd test`.

**Assumptions needing approval:** N1 (analytics and PDF only for ADMIN/OFFICER); shortage = demand of VERIFIED/APPROVED requests minus free stock in ACTIVE warehouses (unexpired); partner offers are shown beside shortages but are not counted as stock.

**Still open (people, not code):** approve the proposed rules (priority weights, fairness cap, officer-only allocation approval, A1, V1, P1, AI1, N1); Divay's D1-D6 and deployment; frontend screens for Days 5-10 (not built); security items listed as open in `docs/SECURITY_REVIEW.md`.

## Frontend Days 5-10 (screens)
Added Vaadin screens for: dashboard, live map, inventory (receive stock, history), shelters (occupancy, alternatives), allocation (recommend, edit, approve, reject), dispatch (timeline, status), volunteers (register, availability, tasks), partners, alerts, report analyzer, analytics (+ PDF), audit log. Each screen hides itself from roles the backend would refuse. Not compiled in the sandbox (no Maven Central): verify with `.\mvnw.cmd clean test` and by clicking through `docs/DEMO_SCRIPT.md`. The VOLUNTEER role has no web screen yet (the backend lists volunteers for staff only).
