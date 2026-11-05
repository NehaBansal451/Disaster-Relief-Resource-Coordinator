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
