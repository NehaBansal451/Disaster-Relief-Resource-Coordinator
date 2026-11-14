# Inventory and Stock Ledger Design (V6–V7 proposal)

**Status:** Design draft for review. The approved V1–V5 ER baseline is unchanged. V6 and V7 have not been applied to a database or merged into GitHub.

**Database:** PostgreSQL 16 + PostGIS 3.4  
**Depends on:** V1–V5 (`warehouses`, `resources`, `users`, `need_requests`)

## 1. Purpose

The system must know how much of each resource is held at each warehouse, keep separate batches where expiry/lot tracking matters, and record every stock movement. Current stock values are used for fast reads; the movement ledger provides the history of how those values changed.

## 2. V6 — `inventory`

One row represents one resource at one warehouse for one batch. An unbatched item uses `batch_number = NULL`; PostgreSQL's `UNIQUE NULLS NOT DISTINCT` constraint makes sure there is only one unbatched row for a warehouse/resource pair.

| Column | Type | Rule / purpose |
|---|---|---|
| `id` | BIGSERIAL | Primary key |
| `warehouse_id` | BIGINT | Required FK to `warehouses`, delete restricted |
| `resource_id` | BIGINT | Required FK to `resources`, delete restricted |
| `batch_number` | VARCHAR(100) | Optional for non-perishable stock; must not be blank |
| `expiry_date` | DATE | Optional for non-perishable stock; required for perishable stock |
| `quantity_on_hand` | INTEGER | Current physical quantity; zero or more |
| `quantity_reserved` | INTEGER | Quantity set aside for approved relief; zero or more and never above on-hand |
| `is_demo` | BOOLEAN | Separates development/demo stock |
| `created_at`, `updated_at` | TIMESTAMPTZ | Audit timestamps |

### Important rules

1. **One stock row per warehouse/resource/batch.** The unique key is `(warehouse_id, resource_id, batch_number)` with NULLs treated as equal.
2. **No negative stock.** `quantity_on_hand >= 0`.
3. **No over-reservation.** `0 <= quantity_reserved <= quantity_on_hand`.
4. **Perishable goods need traceability.** If `resources.perishable = TRUE`, a non-empty batch number and an expiry date are required. A trigger enforces this cross-table rule. Changing a resource from non-perishable to perishable is blocked until existing inventory rows are corrected.
5. **Expired stock remains recordable.** We do not reject an expired lot at insert time: the application must be able to identify it and record a write-off. Allocation queries must exclude expired stock.
6. **Available quantity is calculated, not stored:** `quantity_on_hand - quantity_reserved`.
7. **FEFO:** when selecting perishable stock, prefer the earliest valid expiry date (First Expiry, First Out). The expiry index supports this lookup.
8. **Quantity type:** `INTEGER` matches `need_requests.quantity` and the current API DTOs. If the product later needs fractional kg/litre quantities, change requests, inventory and ledger quantity types together in a planned migration.

### Why batch belongs in the uniqueness key

A warehouse may hold multiple batches of the same resource, with different expiry dates. Each batch needs its own balance so the allocation service can pick stock in FEFO order and avoid mixing expired and unexpired quantities.

## 3. V7 — `inventory_transactions`

Each stock change produces one ledger row. Ledger rows are append-only. A correction is recorded as a new compensating transaction; past movements are never edited or deleted.

| Column | Type | Rule / purpose |
|---|---|---|
| `id` | BIGSERIAL | Primary key |
| `inventory_id` | BIGINT | Required FK to the affected inventory batch |
| `transaction_type` | VARCHAR(20) | Required controlled movement type |
| `quantity_delta_on_hand` | INTEGER | Signed change to physical stock |
| `quantity_delta_reserved` | INTEGER | Signed change to reserved stock |
| `request_id` | BIGINT | Optional FK; required for reservation, release and dispatch movements |
| `actor_user_id` | BIGINT | Required FK to the user who initiated the movement |
| `reason` | TEXT | Required for adjustments and write-offs |
| `external_reference` | VARCHAR(100) | Optional receipt / external document reference |
| `is_demo` | BOOLEAN | Marks development/demo history |
| `created_at` | TIMESTAMPTZ | When the movement was recorded |

### Allowed movement types

| Type | On-hand delta | Reserved delta | Request link | Meaning |
|---|---:|---:|---|---|
| `RECEIPT` | Positive | 0 | Optional / normally none | Stock received into a warehouse |
| `RESERVATION` | 0 | Positive | Required | Stock reserved for a relief request |
| `RELEASE` | 0 | Negative | Required | A previous reservation is released |
| `DISPATCH` | Negative | Same negative amount | Required | Reserved stock physically leaves the warehouse |
| `ADJUSTMENT` | Positive or negative | 0 | Optional | Correction with a mandatory reason |
| `WRITE_OFF` | Negative | 0 | Optional | Damaged/expired stock removed with a mandatory reason |

The migration's CHECK constraint enforces these movement shapes. The `inventory_transactions` table also has triggers rejecting UPDATE and DELETE.

## 4. Required backend transaction pattern

The database design relies on the backend performing each balance update and its ledger insert in **one database transaction**. The backend team must not change inventory balances without inserting the matching ledger record.

### Receive stock

1. Find or create the correct warehouse/resource/batch inventory row (new rows start at zero).
2. Lock the row with `SELECT ... FOR UPDATE`.
3. Increase `quantity_on_hand`.
4. Insert a `RECEIPT` ledger row with the same positive delta.
5. Commit both changes together.

### Reserve stock

1. Lock the relevant inventory row(s) with `SELECT ... FOR UPDATE`, in ascending `inventory.id` order when more than one row is involved.
2. Recalculate available quantity: `on_hand - reserved`.
3. Reject the operation when stock is insufficient or the batch is expired.
4. Increase `quantity_reserved` and insert a `RESERVATION` row linked to the request.
5. Commit both changes together. A database CHECK remains the final protection against an invalid balance.

### Release a reservation

Lock the row, confirm enough quantity is reserved, reduce `quantity_reserved`, and insert a `RELEASE` ledger row in the same transaction.

### Dispatch reserved stock

Lock the row, confirm both on-hand and reserved quantities cover the dispatch, reduce both by the dispatched quantity, and insert a `DISPATCH` row. This keeps available quantity unchanged at dispatch time because the quantity was already reserved.

### Adjust or write off stock

Lock the row and ensure the new on-hand value will not fall below the reserved quantity. Apply the change and insert an `ADJUSTMENT` or `WRITE_OFF` row with a reason, in the same transaction.

If any part fails, the entire transaction must roll back. This avoids the balance changing without its ledger entry, or the ledger entry existing without its balance change.

## 5. Important implementation boundary

- The database stores current balances and validates their allowed range.
- The backend service owns the transactional update sequence and must always write the ledger row.
- The database does not automatically update balances from ledger inserts in this proposal. This keeps stock workflow orchestration visible in the Spring service and matches the project blueprint's `SELECT ... FOR UPDATE` approach.
- The ledger UPDATE/DELETE triggers protect against accidental changes through normal SQL operations. Production permissions should also use a least-privilege application DB role; the application should not connect as a PostgreSQL superuser or table owner.

## 6. Seed data

The demo inventory seed should create the opening inventory rows with their opening `quantity_on_hand`, then insert matching `RECEIPT` transactions in the same Flyway transaction. It must be idempotent: only newly created inventory rows get opening-stock ledger entries. All demo inventory and ledger rows use `is_demo = TRUE` and demo warehouse-manager users as actors.

## 7. Verification checklist

- A resource/warehouse/batch combination can be inserted once.
- A duplicate unbatched row is rejected.
- A perishable resource cannot be stocked without a batch and expiry date.
- Negative on-hand and reserved quantities are rejected.
- Reserved quantity cannot exceed on-hand quantity.
- Each ledger transaction has a valid type/effect combination.
- Reservation, release and dispatch transactions require a request reference.
- Adjustment and write-off transactions require a reason.
- Ledger UPDATE and DELETE are rejected.
- Backend integration tests prove that concurrent reservations cannot oversubscribe the same inventory row.
- Backend integration tests prove balance updates and ledger inserts roll back together.

## 8. Decisions to confirm before merge

| Decision | Proposed choice |
|---|---|
| Batch-level stock | One inventory row per warehouse + resource + batch |
| Unbatched goods | Allow `batch_number = NULL`; only one such row per warehouse/resource |
| Perishable goods | Require both batch number and expiry date |
| Stock quantities | INTEGER to match existing request API; no fractional quantities in this version |
| Available stock | `quantity_on_hand - quantity_reserved` |
| Reservation concurrency | Backend transaction + `SELECT ... FOR UPDATE` + CHECK constraints |
| Ledger corrections | Append compensating movement; never edit/delete history |
| System actor | Require a real user actor for every movement; automated actions must execute under an authorized service user or named system account |
