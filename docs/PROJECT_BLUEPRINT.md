# Project Blueprint (summary)

Source: `Disaster_Relief_Resource_Coordinator_Final_Blueprint.pdf`, version 2.0, 5 November 2026. The PDF is the team's planning reference; this file is a short, version-controlled summary so the repository is self-explanatory. If they disagree, the PDF wins until the team formally approves a change.

## Goal
DRRC turns a reported emergency need into a verified, prioritized, fairly allocated and traceably delivered relief response. Core success measure: one complete chain, demonstrable end to end:

disaster registered -> need reported -> request verified and prioritized -> stock checked -> allocation recommended and approved -> supplies dispatched -> delivery confirmed -> dashboard and audit history update.

Priority scores, allocations and AI output must be explainable, reviewable and overridable by an authorized officer. AI output is a suggestion, never a fact.

## Ownership
| Person | Owns |
|---|---|
| Neha | `backend/`, `frontend/`: Spring Boot APIs, security, business rules, Vaadin UI, integration, tests |
| Divay | `database/`, `deployment/`: schema, migrations, seed data, Docker, CI/CD, hosting, backups |
| Shared | `docs/`: contracts and status, reviewed together |

## Stack (fixed)
Java 21, Spring Boot, Vaadin Flow, PostgreSQL + PostGIS, Spring Security + JWT, Maven, Flyway, JUnit/Mockito, Docker, GitHub Actions, OpenStreetMap, Claude API (backend only). Not allowed as the application stack: React, Angular, Next.js, TypeScript, Node.js (see `SYSTEM_ARCHITECTURE.md` for the build-tooling-only Node policy that still needs approval).

## Roles
Administrator, Relief officer, Warehouse manager, Field worker / volunteer, NGO / partner, Citizen reporter.

## Request lifecycle
Pending -> Verified -> Approved -> Allocated -> Dispatched -> In Transit -> Delivered. Rejected and Cancelled must record a reason. Exact transitions: `API_CONTRACT.md` section 1.

## Feature catalogue (21)
1 Operations dashboard | 2 GIS map | 3 Inventory | 4 Need reporting | 5 Shelters | 6 Explainable priority score | 7 Allocation engine | 8 Shortage/surplus detection | 9 Delivery tracking | 10 Claude report analyzer | 11 Multilingual (EN/HI/PA) | 12 Volunteers | 13 NGO/partners | 14 PDF reports | 15 Limited offline (do not claim until tested) | 16 Weather | 17 Image damage assessment | 18 Audit | 19 Alerts | 20 Awareness centre | 21 AI assistant. Build the core chain first; 15, 17, 20, 21 are the first to defer.

## Priority formula (proposed, not yet approved)
`Priority = 0.40 Severity + 0.30 Population impact + 0.15 Waiting time + 0.15 Time sensitivity`, each input normalized 0-100; thresholds 80/60/40 proposed. Integrity rule: at approval, re-check stock inside a transaction with locking; never double-reserve or go negative.

## Ten-day roadmap and checkpoints
| Day | Neha | Divay | Checkpoint |
|---|---|---|---|
| 1 | Inspect repo, contract | ER draft | Boundaries and run commands agreed |
| 2 | Spring Boot + Vaadin skeleton, login shell | PostGIS, migrations | Apps connect to the database |
| 3 | Auth, roles, disaster and area services/views | Users, disasters, areas, seed | Login, create and list disaster |
| 4 | Request submission, validation, verification, UI | Request/resource/warehouse tables | A request submitted in the UI is saved and retrieved |
| 5 | Inventory APIs and UI, shelters, dashboard queries | Warehouse, inventory, shelter schema | Dashboard and inventory use real records |
| 6 | Priority scoring, allocation, approval | Allocation tables, stock transaction rules | No double allocation |
| 7 | Map UI, dispatch and delivery | Dispatch, delivery, audit, alert schema | Map and timeline reflect real records |
| 8 | Claude analyzer, volunteers/partners | Docker Compose, backup/restore | AI result validated and human-reviewed |
| 9 | Analytics, PDF, audit UI, polish | CI, deployment prep, monitoring | Scope frozen |
| 10 | End-to-end tests, release, rehearsal | Deploy, secrets, backups | Full demo passes |

## Minimum acceptance criteria
Correct-role login with only permitted actions; disaster and area on dashboard/map; request submitted, verified and prioritized with an explanation; allocation recommends stock and shows shortages; approval never over-allocates; dispatch/delivery update request and stock; dashboard and audit reflect the workflow; documented build, test and deploy steps.

## Working rules (from the master prompt)
One milestone at a time; no future milestones early; do not overwrite the other owner's files; agree on API paths, DTOs, enums and transitions first; real persistence only; secrets in environment variables; do not claim anything is complete unless it was actually run; update `PROJECT_STATUS.md` at the end of each day.
