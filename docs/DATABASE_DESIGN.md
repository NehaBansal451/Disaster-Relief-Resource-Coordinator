# Database Design

**Owner:** Divay  
**Database:** PostgreSQL 16 + PostGIS 3.4  
**Schema source of truth:** versioned Flyway migrations in `database/migrations`  
**Baseline:** V1–V5 implemented; ER baseline approved by Divay on 14 November 2026.  
**Next design:** V6 inventory and V7 inventory transaction ledger (proposal; not yet applied).

## 1. Conventions

- Primary keys use `BIGSERIAL id`.
- Timestamps use `TIMESTAMPTZ` and are stored in UTC; the UI displays Asia/Kolkata time.
- Count/quantity fields currently use `INTEGER`, matching the existing `need_requests.quantity` API field.
- Enumerations use `VARCHAR` plus named `CHECK` constraints.
- Constraint names use `ck_<table>_<what>`, `fk_<table>_<what>`, `uq_<table>_<what>` and `idx_<table>_<column>` where practical.
- Spatial columns use PostGIS `geography` and GiST indexes.
- Development/demo rows use `is_demo = TRUE`; fictional text rows are prefixed with `DEMO:` where applicable.
- The dev seed is loaded only under the Spring `dev` profile and is designed to be repeatable/idempotent.
- Never edit an applied migration. Add a new versioned migration instead.

## 2. Built tables — V1 to V5

| Migration | Table | Important columns / rules |
|---|---|---|
| V1/V2 | `organizations` | `id`, `name`, `type`, `contact_email`, `contact_phone`, `is_demo`, `created_at`; name is unique. |
| V1/V2 | `users` | `full_name`, `email`, `password_hash`, `role`, nullable `organization_id`, `enabled`, `preferred_language`, `is_demo`, timestamps; email is unique case-insensitively. |
| V3 | `disasters` | `disaster_type`, `severity`, `status`, start/end dates, nullable `epicentre geography(Point,4326)`, nullable `created_by`; closed disasters require an end time. |
| V3 | `affected_areas` | Required `disaster_id`, area type, district/state, population and impact fields, required `location geography(Point,4326)`, optional `boundary geography(MultiPolygon,4326)`. |
| V4 | `resources` | Resource name, category, unit, perishable flag, shelf-life rule, handling notes and active flag. Name is unique case-insensitively. |
| V4 | `warehouses` | Unique code, optional organization and manager, address/contact fields, optional `capacity_m3`, status, required `location geography(Point,4326)`. |
| V5 | `need_requests` | Disaster, affected area, resource, requester, quantity, affected population, severity, description, status, priority fields and approval fields. |
| V5 | `request_history` | Request, action, from/to status, required actor user, optional reason and timestamp. |

### 2.1 Important V5 constraints

- `quantity > 0`; `affected_population >= 0`; description length 1–2000 after trimming.
- `need_requests.status` allows the future lifecycle statuses `ALLOCATED`, `DISPATCHED`, `IN_TRANSIT` and `DELIVERED`.
- The request's area must belong to the same disaster. V5 adds `UNIQUE (affected_areas.id, affected_areas.disaster_id)` and composite FK `(need_requests.affected_area_id, need_requests.disaster_id)` to `(affected_areas.id, affected_areas.disaster_id)`.
- `REJECTED` and `CANCELLED` requests require a non-blank `status_reason`.
- `APPROVED` requests require `approved_by` and `approved_at`; both columns remain nullable for non-approved states.
- Foreign keys use `ON DELETE RESTRICT` where deleting a parent could erase operational history.
- List/filter indexes exist for request status, severity, disaster, area, resource, requester and creation time.
- Backend request state changes use conditional `UPDATE ... WHERE id = ? AND status = <expected>` and return HTTP 409 if another action already changed the state.

## 3. Approved ER baseline

The V1–V5 relationship design and the attached ER diagram (with the clarifications recorded in `ER_DIAGRAM.md`) were approved by Divay on 14 November 2026.

Key optional relationships:
- `users.organization_id` is nullable.
- `disasters.created_by` is nullable.
- `warehouses.organization_id` and `warehouses.manager_user_id` are nullable.
- `need_requests.approved_by` and `approved_at` are nullable at the column level and required only when status is `APPROVED`.
- `request_history.actor_user_id` is NOT NULL in V5, so every history row must have a user actor.

Exact PostGIS types:
- `disasters.epicentre`: `geography(Point,4326)` (nullable).
- `affected_areas.location`: `geography(Point,4326)` (NOT NULL).
- `affected_areas.boundary`: `geography(MultiPolygon,4326)` (nullable).
- `warehouses.location`: `geography(Point,4326)` (NOT NULL).

Unique constraints / indexes not represented by relationship lines:
- `organizations.name` unique.
- `users.email` unique case-insensitively via index on `lower(email)`.
- `resources.name` unique case-insensitively via index on `lower(name)`.
- `warehouses.code` unique.
- `affected_areas` unique on `(disaster_id, name, district)` with NULLs treated as not distinct.

## 4. Proposed V6 — `inventory`

**Status: draft for review; do not apply until D4 is approved.**

One inventory row represents one warehouse + resource + batch. An unbatched item uses a NULL batch number. V6 adds:

| Column | Type | Purpose |
|---|---|---|
| `id` | BIGSERIAL | Primary key |
| `warehouse_id` | BIGINT | Required FK to `warehouses` |
| `resource_id` | BIGINT | Required FK to `resources` |
| `batch_number` | VARCHAR(100) | Optional for non-perishable stock; required for perishable stock |
| `expiry_date` | DATE | Required for perishable stock; nullable otherwise |
| `quantity_on_hand` | INTEGER | Physical stock, default 0 |
| `quantity_reserved` | INTEGER | Stock allocated/reserved for requests, default 0 |
| `is_demo` | BOOLEAN | Demo-data marker |
| timestamps | TIMESTAMPTZ | Created/updated times |

Rules:
- `UNIQUE NULLS NOT DISTINCT (warehouse_id, resource_id, batch_number)` allows multiple batches but only one unbatched balance row per warehouse/resource.
- `quantity_on_hand >= 0`.
- `0 <= quantity_reserved <= quantity_on_hand`.
- Available stock is calculated as `quantity_on_hand - quantity_reserved`; do not store a second available quantity.
- Perishable inventory needs a non-empty batch number and expiry date. V6 proposes a trigger because this rule depends on `resources.perishable` and cannot be expressed by a simple table CHECK.
- Expired stock remains stored for expiry reporting and write-off. Allocation queries must exclude expired stock.
- FEFO (earliest valid expiry first) is the proposed selection order for perishable stock.
- The `INTEGER` quantity type matches the current request API. Fractional kg/litre support would require a coordinated type change across request, inventory and ledger quantities.

## 5. Proposed V7 — `inventory_transactions`

**Status: draft for review; do not apply until D4 is approved.**

This is the append-only record of every stock movement. It references an inventory batch, the acting user, and optionally the request that caused the movement.

| Column | Type | Purpose |
|---|---|---|
| `id` | BIGSERIAL | Primary key |
| `inventory_id` | BIGINT | Required FK to `inventory` |
| `transaction_type` | VARCHAR(20) | `RECEIPT`, `RESERVATION`, `RELEASE`, `DISPATCH`, `ADJUSTMENT`, `WRITE_OFF` |
| `quantity_delta_on_hand` | INTEGER | Signed change in physical stock |
| `quantity_delta_reserved` | INTEGER | Signed change in reserved stock |
| `request_id` | BIGINT | Nullable FK; mandatory for reserve/release/dispatch |
| `actor_user_id` | BIGINT | Required FK to the user who initiated the movement |
| `reason` | TEXT | Required for adjustment/write-off |
| `external_reference` | VARCHAR(100) | Optional receipt/document reference |
| `is_demo` | BOOLEAN | Demo-data marker |
| `created_at` | TIMESTAMPTZ | Transaction timestamp |

Movement semantics:

| Type | On-hand delta | Reserved delta | Request required? |
|---|---:|---:|---|
| `RECEIPT` | Positive | 0 | No |
| `RESERVATION` | 0 | Positive | Yes |
| `RELEASE` | 0 | Negative | Yes |
| `DISPATCH` | Negative | Same negative quantity | Yes |
| `ADJUSTMENT` | Non-zero signed value | 0 | No |
| `WRITE_OFF` | Negative | 0 | No |

V7's CHECK constraints enforce these movement shapes. UPDATE and DELETE triggers reject changes to ledger rows; corrections must be compensating transactions.

## 6. Required backend behaviour for inventory

All inventory balance updates and their ledger insert must happen in one `@Transactional` operation:

1. Lock the affected inventory row(s) using `SELECT ... FOR UPDATE`.
2. If multiple rows are involved, lock them in ascending `inventory.id` order to reduce deadlocks.
3. Recalculate available stock (`on_hand - reserved`) after acquiring the lock.
4. Reject reservations if the stock is insufficient or the batch has expired.
5. Update `quantity_on_hand` / `quantity_reserved` and insert the matching ledger entry in the same transaction.
6. On any failure, roll back both the balance update and ledger insert.

The DB CHECK constraint is the final defence against a balance where reserved quantity exceeds on-hand quantity. The backend must not modify balances without a matching ledger row.

## 7. Planned tables after V7

| Milestone | Tables | Purpose |
|---|---|---|
| V8 | `shelters` | Capacity, occupancy, facilities and location |
| V9 | `allocations`, `allocation_items` | Request-to-warehouse recommendation and approval |
| V10 | `dispatches`, `delivery_updates` | Shipment and delivery tracking |
| V11 | `audit_logs`, `alerts` | Operational audit and alerts |
| Later | `volunteers`, `volunteer_assignments`, `weather_records`, `ai_analyses`, `awareness_resources` | Supporting features |

Version numbers may be adjusted if the team changes implementation order; applied migration versions must never be reused or edited.

## 8. Verification

- Existing scripts: `database/queries/day2_checks.sql` (22 checks) and `database/queries/day4_checks.sql` (V5 checks). Divay reports that the Day 4 SQL checks have been executed successfully.
- Proposed next script: `database/queries/inventory_checks.sql`, to run after V6 and V7 on PostgreSQL 16.
- Backend integration tests must cover concurrent reservations and transaction rollback. SQL constraints alone cannot prove the Java service uses the required transaction sequence.

## 9. Decisions awaiting approval (D4)

1. One inventory row per warehouse/resource/batch.
2. Perishable resources must have batch and expiry details.
3. Integer stock quantities for the current version.
4. Available quantity = on-hand minus reserved.
5. Reserve/release/dispatch are all linked to a need request.
6. Every stock movement has an actor user.
7. Ledger rows are append-only.
8. Backend transaction + `SELECT ... FOR UPDATE` is responsible for atomic balance and ledger updates.
