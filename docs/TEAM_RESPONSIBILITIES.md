# Team Responsibilities

| Person | Owns | Delivers |
|---|---|---|
| Neha | `backend/`, `frontend/` | Spring Boot APIs, security, business rules (priority, allocation), Vaadin UI, API client, JUnit/Mockito tests |
| Divay | `database/`, `deployment/` | ER design, PostGIS schema, Flyway migrations, seed data, indexes/constraints, Docker Compose, GitHub Actions, hosting, backup/restore |
| Shared | `docs/`, `.github/` contracts | API contract, enums, status transitions, integration guide (reviewed together) |

## Rules
- Edit only your own folders; shared docs by PR with the other person's review.
- Feature branches + PRs into `main`; merge only when build/tests pass and contract compatibility is confirmed.
- Pull latest `main` before work; never overwrite the other person's files.
- Update `docs/PROJECT_STATUS.md` at the end of each working day.

## Hand-offs
| Need | From -> To |
|---|---|
| Migration V2 (FIELD_WORKER role, `ai_analyses`, `is_demo`, priority config) - proposal only, Divay decides (D2) | Neha -> Divay |
| Flyway ownership/execution (D1); only after approval does Neha edit the backend pom | Divay decides, Neha implements |
| `backend` + `frontend` Dockerfiles, Compose services, health checks | Neha supplies run commands/ports -> Divay |
| Node 24 availability for Vaadin production build in CI/Docker | Neha -> Divay |
| Seed data (flood, 3 villages, warehouses, stock) | Divay, reviewed by Neha |
| Concurrency-supporting constraints (A9) | Divay |
