# Release notes: backend prototype (Days 1-10)

## What works (backend)
- Sign-in with roles; disasters and affected areas; need requests with the full state machine, history and cancel rules.
- Inventory with batches, expiry and an append-only stock ledger; warehouses; shelters with occupancy and overcrowding alerts; dashboard.
- Explainable priority scores with officer override; allocation recommendation (nearest stock first, earliest expiry first, fairness cap) and approval that reserves stock exactly once even under concurrent approvals.
- Dispatch, in transit, delivered with stock deduction; audit trail; alerts; map data.
- Volunteers and assignments; partner offers; report analyzer (advisory, scrubbed, human-reviewed); analytics, shortage detection, PDF situation report; login throttle.

## Database
V1-V8 are Divay's (users to shelters). V9 priority/allocation, V10 dispatch/audit/alerts, V11 volunteers/partners/analyzer are the backend's. Run order is automatic (Flyway).

## Known limits
- Frontend screens exist for Days 3-4 only (login, disasters/areas, requests). Days 5-10 are API only.
- Maven build and tests of the Day 5-10 code have been run by the maintainer up to the Days 6-7 branch; the rebuilt branch and Days 8-10 must be run with `.\mvnw.cmd test` before merging.
- See `docs/SECURITY_REVIEW.md` open items, and the assumption register in `docs/PROJECT_STATUS.md`.

## How to run
1. `cd deployment\docker; docker compose up -d`
2. Load `.env` into the PowerShell window, then `cd ..\..\backend`.
3. `.\mvnw.cmd test`, then `.\mvnw.cmd spring-boot:run` (API on http://localhost:8080).
4. Optional analyzer: set `AI_API_KEY`, `AI_MODEL` and `AI_BASE_URL` before starting; without it the analyzer answers 503.
5. For a real deployment use `SPRING_PROFILES_ACTIVE=prod` (migrations only, no demo data; the server refuses to start with demo users present).

## Automated checks
GitHub Actions runs the backend tests (with a PostGIS database) and the frontend tests on every push, plus a weekly code scan. The Maven-test note under "Known limits" above is therefore replaced by the CI result on the Actions tab.
