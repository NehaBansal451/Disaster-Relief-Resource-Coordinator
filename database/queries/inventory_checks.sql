-- V6/V7 database checks. Run after migrations V1-V7 on PostgreSQL 16 + PostGIS 3.4.
-- Creates temporary test fixtures inside a transaction and rolls everything back.
-- Usage: psql -v ON_ERROR_STOP=1 -d <db> -f database/queries/inventory_checks.sql

BEGIN;
CREATE TEMP TABLE _inventory_check_results (name TEXT, ok BOOLEAN) ON COMMIT DROP;
CREATE TEMP TABLE _inventory_fixture (
    user_id BIGINT,
    warehouse_id BIGINT,
    perishable_resource_id BIGINT,
    nonperishable_resource_id BIGINT,
    unbatched_inventory_id BIGINT,
    receipt_transaction_id BIGINT
) ON COMMIT DROP;

CREATE FUNCTION pg_temp.expect_sqlstate(label TEXT, stmt TEXT, expected_state TEXT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    actual_state TEXT;
BEGIN
    BEGIN
        EXECUTE stmt;
        INSERT INTO _inventory_check_results VALUES (label, FALSE);
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS actual_state = RETURNED_SQLSTATE;
        INSERT INTO _inventory_check_results VALUES (label, actual_state = expected_state);
    END;
END;
$$;

-- Fixtures use a unique suffix and are rolled back at the end of this script.
DO $$
DECLARE
    suffix TEXT := pg_backend_pid()::TEXT;
    v_user BIGINT;
    v_warehouse BIGINT;
    v_perishable BIGINT;
    v_nonperishable BIGINT;
    v_inventory BIGINT;
    v_tx BIGINT;
BEGIN
    INSERT INTO users (full_name, email, password_hash, role, enabled, preferred_language)
    VALUES ('Inventory Check Actor', 'inventory-check-' || suffix || '@test.local', 'test-hash', 'ADMIN', TRUE, 'en')
    RETURNING id INTO v_user;

    INSERT INTO resources (name, category, unit, perishable, shelf_life_days, active)
    VALUES ('Inventory Check Perishable ' || suffix, 'FOOD', 'packet', TRUE, 90, TRUE)
    RETURNING id INTO v_perishable;

    INSERT INTO resources (name, category, unit, perishable, shelf_life_days, active)
    VALUES ('Inventory Check Durable ' || suffix, 'SHELTER', 'piece', FALSE, NULL, TRUE)
    RETURNING id INTO v_nonperishable;

    INSERT INTO warehouses (code, name, location, status)
    VALUES ('IC-' || suffix, 'Inventory Check Warehouse',
            ST_SetSRID(ST_MakePoint(76.3869, 30.3398), 4326)::geography, 'ACTIVE')
    RETURNING id INTO v_warehouse;

    INSERT INTO inventory (warehouse_id, resource_id, batch_number, expiry_date, is_demo)
    VALUES (v_warehouse, v_nonperishable, NULL, NULL, FALSE)
    RETURNING id INTO v_inventory;

    -- Simulate the backend's single @Transactional receipt operation: update the balance
    -- and insert its matching ledger row before the transaction commits.
    UPDATE inventory SET quantity_on_hand = quantity_on_hand + 100 WHERE id = v_inventory;

    INSERT INTO inventory_transactions (
        inventory_id, transaction_type, quantity_delta_on_hand, quantity_delta_reserved,
        actor_user_id, reason, external_reference
    ) VALUES (
        v_inventory, 'RECEIPT', 100, 0, v_user, NULL, 'CHECK-RECEIPT-' || suffix
    ) RETURNING id INTO v_tx;

    INSERT INTO _inventory_fixture VALUES
        (v_user, v_warehouse, v_perishable, v_nonperishable, v_inventory, v_tx);
END;
$$;

INSERT INTO _inventory_check_results
SELECT 'opening receipt balance matches its ledger delta',
       i.quantity_on_hand = 100 AND t.quantity_delta_on_hand = 100
  FROM inventory i
  JOIN inventory_transactions t ON t.inventory_id = i.id
  JOIN _inventory_fixture f ON f.unbatched_inventory_id = i.id
 WHERE t.id = (SELECT receipt_transaction_id FROM _inventory_fixture);

-- A perishable resource must have both a lot/batch identifier and expiry.
SELECT pg_temp.expect_sqlstate(
    'perishable stock without batch/expiry is rejected',
    format($q$INSERT INTO inventory (warehouse_id, resource_id, batch_number, expiry_date)
           SELECT warehouse_id, perishable_resource_id, NULL, NULL FROM _inventory_fixture$q$),
    '23514'
);

-- Valid perishable batch is accepted.
INSERT INTO inventory (warehouse_id, resource_id, batch_number, expiry_date)
SELECT warehouse_id, perishable_resource_id, 'CHECK-BATCH-' || pg_backend_pid(), CURRENT_DATE + 90
FROM _inventory_fixture;
INSERT INTO _inventory_check_results VALUES ('perishable stock with batch and expiry is accepted', TRUE);

-- Unbatched unique key treats NULL as equal.
SELECT pg_temp.expect_sqlstate(
    'duplicate unbatched warehouse/resource row is rejected',
    format($q$INSERT INTO inventory (warehouse_id, resource_id, batch_number, expiry_date)
           SELECT warehouse_id, nonperishable_resource_id, NULL, NULL FROM _inventory_fixture$q$),
    '23505'
);

-- Balance integrity.
SELECT pg_temp.expect_sqlstate(
    'negative on-hand quantity is rejected',
    format($q$UPDATE inventory SET quantity_on_hand = -1 WHERE id = (SELECT unbatched_inventory_id FROM _inventory_fixture)$q$),
    '23514'
);
SELECT pg_temp.expect_sqlstate(
    'reserved quantity greater than on-hand is rejected',
    format($q$UPDATE inventory SET quantity_on_hand = 10, quantity_reserved = 11
           WHERE id = (SELECT unbatched_inventory_id FROM _inventory_fixture)$q$),
    '23514'
);

-- Ledger effect rules.
SELECT pg_temp.expect_sqlstate(
    'reservation without request reference is rejected',
    format($q$INSERT INTO inventory_transactions
           (inventory_id, transaction_type, quantity_delta_on_hand, quantity_delta_reserved, actor_user_id)
           SELECT unbatched_inventory_id, 'RESERVATION', 0, 5, user_id FROM _inventory_fixture$q$),
    '23514'
);
SELECT pg_temp.expect_sqlstate(
    'dispatch with unequal on-hand/reserved deltas is rejected',
    format($q$INSERT INTO inventory_transactions
           (inventory_id, transaction_type, quantity_delta_on_hand, quantity_delta_reserved,
            request_id, actor_user_id)
           SELECT unbatched_inventory_id, 'DISPATCH', -5, -4, NULL, user_id FROM _inventory_fixture$q$),
    '23514'
);
SELECT pg_temp.expect_sqlstate(
    'adjustment without a reason is rejected',
    format($q$INSERT INTO inventory_transactions
           (inventory_id, transaction_type, quantity_delta_on_hand, quantity_delta_reserved, actor_user_id)
           SELECT unbatched_inventory_id, 'ADJUSTMENT', -1, 0, user_id FROM _inventory_fixture$q$),
    '23514'
);

-- Ledger immutability.
SELECT pg_temp.expect_sqlstate(
    'ledger UPDATE is rejected',
    format($q$UPDATE inventory_transactions SET reason = 'tampered'
           WHERE id = (SELECT receipt_transaction_id FROM _inventory_fixture)$q$),
    '55000'
);
SELECT pg_temp.expect_sqlstate(
    'ledger DELETE is rejected',
    format($q$DELETE FROM inventory_transactions
           WHERE id = (SELECT receipt_transaction_id FROM _inventory_fixture)$q$),
    '55000'
);

-- Required indexes exist.
INSERT INTO _inventory_check_results
SELECT 'inventory FEFO index exists',
       EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_inventory_fefo');
INSERT INTO _inventory_check_results
SELECT 'ledger inventory/time index exists',
       EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_inventory_transactions_inventory_time');

SELECT CASE WHEN ok THEN 'PASS' ELSE 'FAIL' END AS result, name
FROM _inventory_check_results
ORDER BY ok, name;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM _inventory_check_results WHERE NOT ok) THEN
        RAISE EXCEPTION 'Inventory checks FAILED';
    END IF;
END;
$$;

ROLLBACK;
