# DRRC System Architecture (v0.2 draft)

Status: Day 1 direction approved in principle; details below are **PROPOSED** until approved by Neha and Divay. Items tagged **[Divay D1-D6]** need Divay's approval (section 6). Source of truth: Final Project Blueprint v2.0. The request/allocation state machine now lives only in `API_CONTRACT.md` section 1.

## 1. Verified repository state (commit 2dae6e4)
| Area | Actual content |
|---|---|
| backend/ | Spring Boot **4.1.2**, Java 21, Maven wrapper, package `com.disasterrelief.backend`. Deps: webmvc, actuator, validation (+ test starters). One empty `contextLoads` test. No JPA, Flyway, Security, JWT, DB driver. |
| frontend/ | Only `.gitkeep`. |
| database/ | `migrations/V1__init_users_organizations.sql` (PostGIS, `organizations`, `users`). |
| deployment/ | `docker/docker-compose.yml` (PostGIS 16-3.4 only) and `.env.example`. |
| .github/workflows, docs/ | Empty. |
Not built or tested on Day 1.

## 2. Architecture decisions
| # | Decision | Status |
|---|---|---|
| A1 | Two Maven apps: `backend` (REST, 8080) and `frontend` (Vaadin Flow, 8081); frontend talks to backend only via REST. | Approved in principle |
| A2 | Vaadin 25.x (requires Spring Boot 4.1+; matches current parent). | Approved in principle |
| A3 | Keep package root `com.disasterrelief.backend`; frontend `com.disasterrelief.frontend`. Blueprint tree to be updated. | Proposed |
| A4 | Layered modular monolith, package-by-feature; controllers use services only; DTOs only across the API. | Approved in principle |
| A5 | Vaadin calls backend server-to-server (`RestClient`), JWT kept in Vaadin session; CORS only for tooling. | Proposed |
| A6 | Spring Security, stateless JWT (HS256, 60 min, no refresh in MVP), BCrypt, `@PreAuthorize`; `JWT_SECRET` env var. | Proposed |
| A7 | Flyway runs inside the backend at startup; `database/migrations/` copied into backend build as `db/migration`; Hibernate `ddl-auto=validate`. | **[Divay D1]** not decided |
| A8 | PostGIS `geography(Point,4326)` + GiST; straight-line `ST_Distance` for MVP; no road routing. | **[Divay D3]** |
| A9 | Stock integrity: see section 4. | **[Divay D4]** |
| A10 | Allocations persisted (`RECOMMENDED -> APPROVED/REJECTED/STALE`, `version`); approval re-checks stock. Workflow in `API_CONTRACT.md` section 4. | Proposed |
| A11 | No backend WebSocket in MVP; Vaadin `@Push` + timed refresh. | Proposed |
| A12 | Claude only in backend `integration.ai`; output stored in `ai_analyses`; cannot write operational records. | Approved in principle; table **[Divay D2/D3]** |
| A13 | RFC 9457 problem JSON with stable `code`; pagination `page,size,sort`. | Proposed |
| A14 | Secrets only via environment variables; `.env` git-ignored. | Approved in principle |
| A15 | JUnit 5, Mockito, Testcontainers (postgis image); `contextLoads` must move to Testcontainers once a datasource exists. | Proposed |
| A16 | Request approval (`VERIFIED -> APPROVED`) and allocation approval are separate actions, endpoints and audit events (`API_CONTRACT.md` sections 1-2, 4). | Approved by review |

## 3. Priority scoring - PROPOSED, NOT APPROVED
Neither the weights nor the thresholds are final. They become approved only when both Neha and Divay record approval in `PROJECT_STATUS.md`.
- Proposed formula (blueprint starting formula): `Priority = 0.40*Severity + 0.30*PopulationImpact + 0.15*WaitingTime + 0.15*TimeSensitivity`, inputs normalised to 0-100.
- Proposed categories: Critical >= 80, High 60-79.99, Medium 40-59.99, Low < 40 (blueprint example 85.5 would be Critical).
- Weights must be configurable; where they are stored (table vs properties) is part of **[Divay D2/D3]**.
- Normalisation rules for each input are written and approved before Day 6 coding. Recalculation as waiting time grows: open decision O5.

## 4. Inventory integrity - PROPOSED **[Divay D4]**
- `inventory(quantity_on_hand, quantity_reserved)` with `CHECK (quantity_reserved >= 0 AND quantity_reserved <= quantity_on_hand)`; available = on_hand - reserved.
- Reservation in one `@Transactional` method: `SELECT ... FOR UPDATE` on inventory rows in ascending id order, re-check, then reserve; the CHECK is the backstop.
- Dispatch reduces on_hand and reserved by the same amount.
- Every change appends to `inventory_transactions`; append-only enforcement (trigger or revoked UPDATE/DELETE) to be decided by Divay.
- Uniqueness of warehouse + resource + batch must handle NULL batch (PostgreSQL treats NULLs as distinct unless handled).

## 5. Draft ER design - PROPOSED, ownership Divay **[Divay D3]**
Existing: `organizations`, `users`. Relationships (BIGSERIAL ids, TIMESTAMPTZ audit columns):
- `disasters` 1-N `affected_areas`; `affected_areas` 1-N `need_requests`; `resources` 1-N `need_requests`; `users` 1-N `need_requests`; `need_requests` 1-N `request_history` (includes approval and clarification events)
- `warehouses` 1-N `inventory` N-1 `resources`; `inventory` 1-N `inventory_transactions`
- `need_requests` 1-N `allocations` 1-N `allocation_items`; `allocations` 1-N `dispatches` 1-N `delivery_updates`
- `shelters`, `volunteers`/`volunteer_assignments`, `alerts`, `audit_logs`, `weather_records`, `awareness_resources`
- Proposed additions: `ai_analyses`, priority override data, priority weight configuration.
Indexes: need_requests(status, area, priority), inventory(warehouse, resource), transactions(created_at), GiST on geography columns. Final columns go in Divay's `docs/DATABASE_DESIGN.md` (not created yet).

## 6. Decisions requiring Divay's approval
| ID | Decision | Neha's proposal (not final) | Divay decides |
|---|---|---|---|
| D1 | Flyway ownership and execution | Divay owns SQL files; backend executes Flyway at startup using copied resources. Alternatives: Flyway container/CLI step in `deployment/` before backend. Never edit applied V1; changes are new versions. | Which option; DB user privileges (DDL); multi-instance behaviour; production migration sequence |
| D2 | Migration V2 | Add `FIELD_WORKER` to the role CHECK; `ai_analyses`; `is_demo` flag; priority configuration; request approval fields/history support | Exact content, naming, order, rollback approach |
| D3 | Database ER design | Section 5 | Final tables, columns, normalisation, PostGIS types, `DATABASE_DESIGN.md` |
| D4 | Inventory constraints | Section 4 | CHECKs, uniqueness with NULL batch, ledger immutability, locking approach |
| D5 | Demo data | Separate seed set, rows flagged `is_demo`, loaded only in demo profile/environment, demo passwords clearly marked | Location (`database/seed/`), loading mechanism, labelling, reset procedure |
| D6 | Deployment architecture | Two app services + PostGIS DB; Dockerfiles/Compose in `deployment/`; ports 8080/8081; env var list agreed with Neha | Single vs separate services, hosting provider (free-tier limits, PostGIS, persistence, sleep), CI/CD, backups, Node in CI |

## 7. Vaadin / Node.js build tooling (needs both members' approval)
- Fact (Vaadin 25 docs): building the Vaadin frontend requires Node.js (24 or later is stated for Vaadin 25). This was not yet verified in this repo.
- Proposed policy: Node.js and npm are **build-time tooling invoked by the Vaadin Maven plugin only**. They are not the backend, not a server, not an application runtime, and carry no team-written application code.
- Not allowed: Node/Express or any Node service, TypeScript/JavaScript application code, React/Hilla views, npm packages added by us other than what a third-party Vaadin component strictly requires (each such use documented).
- The backend remains Spring Boot (Java). Production is expected to need only a JVM; to be verified on the first production build.
- CI and Docker images must provide the required Node version **[Divay D6]**.
- `.gitignore` currently excludes `frontend/package.json` and lock file. Whether to track them for reproducible builds is open (changing `.gitignore` is a later, approved change; not touched now).

## 8. Integration requirements
1. Frontend base URL for backend: `DRRC_BACKEND_URL` (default `http://localhost:8080`).
2. DTO shapes only via `API_CONTRACT.md`; no shared Maven module for now.
3. Demo data is separate and labelled (D5).
4. Compatibility check on Day 2: Boot 4 / Jackson 3 / Vaadin 25 startup and JSON smoke test.
