# Next steps (20 Nov 2026)

The inventory design package that this file used to describe (V6/V7) is already merged, so those instructions were removed. This is the current list, in order.

## 1. Make sure the automated checks are green
Open the repository's **Actions** tab on GitHub. "CI" must show both "Backend tests" and "Frontend tests" green. If one is red, open the failing step and fix the first `[ERROR]` line.

## 2. Rehearse the demo
Follow `DEMO_SCRIPT.md` from top to bottom on a local run (`INTEGRATION_GUIDE.md`). Write every difference in its rehearsal log and fix it.

## 3. Record the decisions
Neha and Divay each go through `APPROVALS.md`, mark every row `approved` or `changed: ...`, then update the register in `PROJECT_STATUS.md`.

## 4. Deployment (Divay)
Decision D6: add backend and frontend services to Docker Compose, choose a host, then HTTPS, secrets, backups and monitoring. Run the backend with `SPRING_PROFILES_ACTIVE=prod`, never `dev`.

## 5. Before any public use
Work through the open items in `SECURITY_REVIEW.md`: persistent login rate limit, token revocation, and a dependency scan review (Dependabot alerts must be switched on in the repository settings).
