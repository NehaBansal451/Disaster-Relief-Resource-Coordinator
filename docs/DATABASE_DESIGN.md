# Database Design

Owner: Divay (proposals by Neha pending his review, decisions D1-D4 in `PROJECT_STATUS.md`). Source of truth: Flyway migrations in `database/migrations`. This file describes what is **built** (V1-V5) and what is **planned**. PostgreSQL 16 + PostGIS 3.4.

## Conventions
- Primary keys `BIGSERIAL id`. Timestamps `TIMESTAMPTZ` (stored in UTC, shown in Asia/Kolkata by the UI). Money-like or counted values are integers with CHECK constraints.
- Enumerations are `VARCHAR` plus a named `CHECK` (easy to extend in a later migration). Constraint names: `ck_<table>_<what>`, `fk_<table>_<what>`, `idx_<table>_<column>`.
- Geography columns use `geography(Point,4326)` with GIST indexes; the backend reads and writes them with SQL (`ST_SetSRID(ST_MakePoint(lon,lat),4326)::geography`), not JPA.
- Demo rows have `is_demo = TRUE` and descriptions starting `DEMO:`. The dev seed (`database/seed/R__dev_seed.sql`) runs only under the `dev` Spring profile and is idempotent.
- Never edit an applied migration; add a new `V<n>`.

## Built tables
| Migration | Table | Purpose and key rules |
|---|---|---|
| V1 | `organizations` | NGOs/partners/government units. V2 adds contact fields. |
| V1/V2 | `users` | Accounts: unique email (case-insensitive index), BCrypt hash, `role` CHECK (ADMIN, OFFICER, WAREHOUSE_MANAGER, FIELD_WORKER, NGO, VOLUNTEER, CITIZEN), `enabled`, `preferred_language`, `is_demo`. |
| V3 | `disasters` | Type, severity, status (ACTIVE/MONITORING/CLOSED), `started_at`/`ended_at` with date-order and closed-has-end checks, `epicentre geography(Point)`. |
| V3 | `affected_areas` | Belongs to a disaster (RESTRICT delete), area type, population estimate, impact, `location geography(Point)`, GIST index. |
| V4 | `resources` | Catalogue: category CHECK, `unit`, perishable with shelf-life rule, `active`. |
| V4 | `warehouses` | Unique `code`, `location geography(Point)` + GIST, `capacity_m3` (unit is a proposal). |
| V5 | `need_requests` | See below. |
| V5 | `request_history` | One row per workflow event; `action` CHECK (CREATED, VERIFIED, REJECTED, CLARIFICATION_REQUESTED, APPROVED, CANCELLED); indexed by request and actor. |

### `need_requests` (V5)
Columns: `disaster_id`, `affected_area_id`, `resource_id`, `requested_by`, `quantity`, `affected_population`, `severity`, `description`, `status`, `status_reason`, `priority_score`, `priority_category`, `approved_by`, `approved_at`, `is_demo`, `created_at`, `updated_at`.

Integrity enforced by the database, not only by Java:
- `quantity > 0`, `affected_population >= 0`, description 1-2000 characters after trimming.
- Status CHECK includes the later lifecycle states (ALLOCATED, DISPATCHED, IN_TRANSIT, DELIVERED) so Day 6-7 need no constraint change.
- **Area must belong to the same disaster**: V5 adds `UNIQUE (id, disaster_id)` on `affected_areas` and a composite foreign key `(affected_area_id, disaster_id)`.
- REJECTED/CANCELLED require a non-blank `status_reason`; APPROVED requires `approved_by` and `approved_at`.
- `priority_score` 0-100 and `priority_category` are nullable until Day 6 scoring exists.
- Foreign keys are RESTRICT: a disaster, resource or user with requests cannot be deleted.
- Indexes on status, severity, disaster, area, resource, requester and `created_at DESC` (all list filters).

Race safety: the backend changes status with `UPDATE ... WHERE id = ? AND status = <expected>` and treats 0 rows as "no longer in that state" (HTTP 409), so two officers cannot both act on the same request.

## Planned tables (blueprint section 8, not built yet)
| Day | Tables |
|---|---|
| 5 | `inventory` (warehouse x resource, batch/expiry, `quantity_on_hand`, `quantity_reserved` with `reserved <= on_hand`), `inventory_transactions` (append-only ledger), `shelters` |
| 6 | `allocations`, `allocation_items` (version column for stale-recommendation detection), priority override records |
| 7 | `dispatches`, `delivery_updates`, `audit_logs`, `alerts` |
| 8+ | `volunteers`, `volunteer_assignments`, `ai_analyses`, `weather_records`, `awareness_resources` |

Stock rule to agree before Day 5 (D4): available = on_hand - reserved; dispatched quantities leave `on_hand` through a ledger row, so nothing is counted twice; reservation inside one transaction with `SELECT ... FOR UPDATE` on the inventory row plus a CHECK constraint as the last line of defence.

## Verification
`database/queries/day2_checks.sql` (22 checks) and `database/queries/day4_checks.sql` (18 checks for V5) run inside a rolled-back transaction: `psql -v ON_ERROR_STOP=1 -d <db> -f database/queries/day4_checks.sql`. Every line must print `PASS`.

## Open points for Divay
D1 Flyway ownership | D2 V2 content | D3 this ER baseline | D4 inventory constraints | resource catalogue as reference data vs dev seed | `capacity_m3` unit | V5 composite foreign key approach.
