# Next step — review and apply V6/V7 inventory design

This package contains the proposed implementation files for inventory and its stock movement ledger. It is not yet committed to GitHub and the migrations have not been run.

## Files to place in the repository

| Package file | Repository destination |
|---|---|
| `V6__create_inventory.sql` | `database/migrations/V6__create_inventory.sql` |
| `V7__create_inventory_transactions.sql` | `database/migrations/V7__create_inventory_transactions.sql` |
| `R__z_dev_inventory_seed.sql` | `database/seed/R__z_dev_inventory_seed.sql` |
| `inventory_checks.sql` | `database/queries/inventory_checks.sql` |
| `DATABASE_DESIGN.md` | `docs/DATABASE_DESIGN.md` |
| `ER_DIAGRAM.md` | `docs/ER_DIAGRAM.md` |
| `INVENTORY_DESIGN.md` | `docs/INVENTORY_DESIGN.md` |

`ER_DIAGRAM_V1_V7.html` is a browser-viewable copy of the diagram using the approved V1–V5 model and adding V6/V7 as proposed entities. It is a review artifact, not a replacement for the Markdown file in the repository.

## Before applying

1. Review `INVENTORY_DESIGN.md`, especially the decisions in section 8.
2. Confirm the backend developer agrees to the transaction pattern: lock inventory rows, update balances, and insert the corresponding ledger row in the same `@Transactional` method.
3. Back up any existing development database before testing migrations.
4. Apply migrations to a clean test database first. Flyway should apply V1–V7 in order. The new `R__z_dev_inventory_seed.sql` then creates fictional opening balances and matching receipt ledger entries after the existing demo seed.

## Verify the database

Run the script against a database where V1–V7 and the dev seed have been applied:

```bash
psql -v ON_ERROR_STOP=1 -d disaster_relief -f database/queries/inventory_checks.sql
```

Every test should print `PASS`. The script uses temporary fixtures inside a transaction and rolls back at the end.

## Required backend follow-up

The SQL migrations do not implement the stock service. The backend needs an `InventoryService` that uses `@Transactional` and `SELECT ... FOR UPDATE` for receipt, reserve, release, dispatch, adjustment and write-off. It must update the inventory balance and insert the matching ledger row in the same transaction.

Required integration tests:
- two concurrent reservations cannot reserve more than available stock;
- dispatch reduces both on-hand and reserved by the same quantity;
- a failed ledger insert rolls back the balance update;
- expired stock is not offered for allocation;
- reservations across multiple batches lock rows in ascending inventory ID order.

## Test limitation

This environment does not have `psql`, Docker or a PostgreSQL server installed. The SQL files were checked for consistency against the current V1–V5 schema and PostgreSQL 16 syntax references, but these migrations and checks have **not** been executed here. Run them in the team's local PostgreSQL/PostGIS environment before merging.
