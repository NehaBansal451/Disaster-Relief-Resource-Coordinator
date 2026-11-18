-- Day 6-7 database self-check (V9 priority/allocation, V10 dispatch/audit/alerts). Run after V1-V10 and the dev seed:
--   psql -v ON_ERROR_STOP=1 -d disaster_relief -f database/queries/day7_checks.sql
-- Every line must start with PASS. All fixtures are rolled back.
\set ON_ERROR_STOP off
BEGIN;
CREATE TEMP TABLE r(test text, outcome text);
INSERT INTO need_requests (disaster_id, affected_area_id, resource_id, requested_by, quantity, severity, description)
SELECT a.disaster_id, a.id, (SELECT min(id) FROM resources), (SELECT min(id) FROM users), 5, 'LOW', 'day7-check' FROM affected_areas a LIMIT 1;

DO $$ BEGIN
  INSERT INTO allocations (request_id, requested_qty, allocated_qty, created_by)
    SELECT id, 5, 5, requested_by FROM need_requests WHERE description='day7-check';
  INSERT INTO r VALUES ('first live allocation accepted','PASS');
EXCEPTION WHEN OTHERS THEN INSERT INTO r VALUES ('first live allocation accepted','FAIL '||SQLERRM); END $$;

DO $$ BEGIN
  INSERT INTO allocations (request_id, requested_qty, allocated_qty, created_by)
    SELECT id, 5, 5, requested_by FROM need_requests WHERE description='day7-check';
  INSERT INTO r VALUES ('second live allocation for one request refused','FAIL accepted');
EXCEPTION WHEN unique_violation THEN INSERT INTO r VALUES ('second live allocation for one request refused','PASS'); END $$;

DO $$ BEGIN
  INSERT INTO allocations (request_id, requested_qty, allocated_qty, created_by)
    SELECT id, 5, 9, requested_by FROM need_requests WHERE description='day7-check';
  INSERT INTO r VALUES ('allocated above requested refused','FAIL accepted');
EXCEPTION WHEN check_violation THEN INSERT INTO r VALUES ('allocated above requested refused','PASS');
          WHEN unique_violation THEN INSERT INTO r VALUES ('allocated above requested refused','PASS'); END $$;

DO $$ BEGIN
  UPDATE need_requests SET priority_score = 50 WHERE description='day7-check';
  INSERT INTO r VALUES ('priority score without category refused','FAIL accepted');
EXCEPTION WHEN check_violation THEN INSERT INTO r VALUES ('priority score without category refused','PASS'); END $$;

DO $$ BEGIN
  INSERT INTO audit_logs (actor_user_id, actor_role, action, entity_type, entity_id)
    SELECT id, role, 'CHECK','need_requests',1 FROM users LIMIT 1;
  DELETE FROM audit_logs;
  INSERT INTO r VALUES ('audit log delete refused','FAIL deleted');
EXCEPTION WHEN OTHERS THEN INSERT INTO r VALUES ('audit log delete refused','PASS'); END $$;

DO $$ BEGIN
  UPDATE inventory SET quantity_reserved = quantity_on_hand + 1 WHERE id = (SELECT min(id) FROM inventory);
  INSERT INTO r VALUES ('reserved above on-hand refused','FAIL accepted');
EXCEPTION WHEN check_violation THEN INSERT INTO r VALUES ('reserved above on-hand refused','PASS'); END $$;

SELECT outcome || '  ' || test FROM r ORDER BY 1;
ROLLBACK;
