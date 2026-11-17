-- Day 5 self-check for V6 (inventory, inventory_transactions, shelters). Rolled back; every line must print PASS.
-- Run: psql -v ON_ERROR_STOP=1 -d <db> -f database/queries/day5_checks.sql
BEGIN;
CREATE TEMP TABLE _r(name text, ok boolean);
CREATE OR REPLACE FUNCTION pg_temp.must_fail(label text, stmt text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  BEGIN EXECUTE stmt; INSERT INTO _r VALUES (label, false);
  EXCEPTION WHEN check_violation OR foreign_key_violation OR unique_violation OR not_null_violation THEN INSERT INTO _r VALUES (label, true);
  END;
END $$;
SELECT pg_temp.must_fail('negative on-hand rejected',  $q$UPDATE inventory SET quantity_on_hand = -1 WHERE id = (SELECT min(id) FROM inventory)$q$);
SELECT pg_temp.must_fail('reserved above on-hand rejected (no over-reservation)', $q$UPDATE inventory SET quantity_reserved = quantity_on_hand + 1 WHERE id = (SELECT min(id) FROM inventory)$q$);
SELECT pg_temp.must_fail('negative reserved rejected', $q$UPDATE inventory SET quantity_reserved = -1 WHERE id = (SELECT min(id) FROM inventory)$q$);
SELECT pg_temp.must_fail('duplicate batch in same warehouse rejected', $q$INSERT INTO inventory (warehouse_id, resource_id, batch_ref, quantity_on_hand)
    SELECT warehouse_id, resource_id, batch_ref, 1 FROM inventory LIMIT 1$q$);
SELECT pg_temp.must_fail('unknown warehouse rejected', $q$INSERT INTO inventory (warehouse_id, resource_id, quantity_on_hand) VALUES (-1, (SELECT min(id) FROM resources), 1)$q$);
SELECT pg_temp.must_fail('blank batch rejected', $q$INSERT INTO inventory (warehouse_id, resource_id, batch_ref, quantity_on_hand)
    VALUES ((SELECT min(id) FROM warehouses), (SELECT max(id) FROM resources), '  ', 1)$q$);
SELECT pg_temp.must_fail('ledger: unknown type rejected', $q$INSERT INTO inventory_transactions (inventory_id, warehouse_id, resource_id, type, quantity, on_hand_after, reserved_after, actor_user_id)
    SELECT id, warehouse_id, resource_id, 'STEAL', 1, 1, 0, (SELECT min(id) FROM users) FROM inventory LIMIT 1$q$);
SELECT pg_temp.must_fail('ledger: zero quantity rejected', $q$INSERT INTO inventory_transactions (inventory_id, warehouse_id, resource_id, type, quantity, on_hand_after, reserved_after, actor_user_id)
    SELECT id, warehouse_id, resource_id, 'RECEIPT', 0, 1, 0, (SELECT min(id) FROM users) FROM inventory LIMIT 1$q$);
SELECT pg_temp.must_fail('ledger: negative receipt rejected', $q$INSERT INTO inventory_transactions (inventory_id, warehouse_id, resource_id, type, quantity, on_hand_after, reserved_after, actor_user_id)
    SELECT id, warehouse_id, resource_id, 'RECEIPT', -5, 1, 0, (SELECT min(id) FROM users) FROM inventory LIMIT 1$q$);
SELECT pg_temp.must_fail('ledger rows cannot be updated', $q$UPDATE inventory_transactions SET note = 'tampered'$q$);
SELECT pg_temp.must_fail('ledger rows cannot be deleted', $q$DELETE FROM inventory_transactions$q$);
SELECT pg_temp.must_fail('deleting a warehouse with stock is blocked', $q$DELETE FROM warehouses WHERE id = (SELECT min(warehouse_id) FROM inventory)$q$);
SELECT pg_temp.must_fail('shelter capacity 0 rejected', $q$INSERT INTO shelters (name, capacity, location) VALUES ('x', 0, ST_SetSRID(ST_MakePoint(76,30),4326)::geography)$q$);
SELECT pg_temp.must_fail('shelter negative occupancy rejected', $q$INSERT INTO shelters (name, capacity, occupancy, location) VALUES ('x', 10, -1, ST_SetSRID(ST_MakePoint(76,30),4326)::geography)$q$);
SELECT pg_temp.must_fail('shelter bad status rejected', $q$INSERT INTO shelters (name, capacity, status, location) VALUES ('x', 10, 'FULL', ST_SetSRID(ST_MakePoint(76,30),4326)::geography)$q$);
SELECT pg_temp.must_fail('shelter without location rejected', $q$INSERT INTO shelters (name, capacity) VALUES ('x', 10)$q$);
INSERT INTO _r SELECT 'overcrowded shelter allowed (occupancy above capacity)', (SELECT count(*) FROM shelters WHERE occupancy > capacity) >= 1;
INSERT INTO _r SELECT 'demo stock seeded for 3 warehouses', (SELECT count(DISTINCT warehouse_id) FROM inventory) >= 3;
INSERT INTO _r SELECT 'every seeded stock row has an opening ledger entry',
  NOT EXISTS (SELECT 1 FROM inventory i WHERE NOT EXISTS (SELECT 1 FROM inventory_transactions t WHERE t.inventory_id = i.id));
INSERT INTO _r SELECT 'ledger opening balance equals on-hand',
  NOT EXISTS (SELECT 1 FROM inventory i WHERE i.quantity_on_hand <> (SELECT coalesce(sum(t.quantity),0) FROM inventory_transactions t WHERE t.inventory_id = i.id AND t.type = 'RECEIPT'));
INSERT INTO _r SELECT 'spatial index on shelters exists', EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_shelters_location');
INSERT INTO _r SELECT 'nearest-shelter query works', (SELECT count(*) FROM shelters s2, shelters s1 WHERE s1.id = (SELECT min(id) FROM shelters) AND s2.id <> s1.id AND ST_Distance(s1.location, s2.location) > 0) >= 1;
SELECT CASE WHEN ok THEN 'PASS' ELSE 'FAIL' END AS result, name FROM _r ORDER BY ok, name;
ROLLBACK;
