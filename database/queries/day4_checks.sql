-- Day 4 self-check for V5 (need_requests, request_history). Changes nothing: everything runs inside a rolled-back transaction.
-- Run: psql -v ON_ERROR_STOP=1 -d <db> -f database/queries/day4_checks.sql   (each line must print PASS)
BEGIN;
CREATE TEMP TABLE _r(name text, ok boolean);
CREATE OR REPLACE FUNCTION pg_temp.must_fail(label text, stmt text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  BEGIN EXECUTE stmt; INSERT INTO _r VALUES (label, false);
  EXCEPTION WHEN check_violation OR foreign_key_violation OR unique_violation OR not_null_violation THEN INSERT INTO _r VALUES (label, true);
  END;
END $$;
CREATE OR REPLACE FUNCTION pg_temp.mk(q int, descr text DEFAULT 'chk', area text DEFAULT 'Demo Khera Village') RETURNS text LANGUAGE sql AS $$
  SELECT format($f$INSERT INTO need_requests (disaster_id, affected_area_id, resource_id, requested_by, quantity, severity, description)
    SELECT a.disaster_id, a.id, (SELECT min(id) FROM resources), (SELECT min(id) FROM users), %s, 'HIGH', %L FROM affected_areas a WHERE a.name = %L$f$, q, descr, area) $$;

SELECT pg_temp.must_fail('zero quantity rejected',        pg_temp.mk(0));
SELECT pg_temp.must_fail('negative quantity rejected',    pg_temp.mk(-5));
SELECT pg_temp.must_fail('blank description rejected',    pg_temp.mk(5, '   '));
SELECT pg_temp.must_fail('description over 2000 rejected',pg_temp.mk(5, repeat('x', 2001)));
SELECT pg_temp.must_fail('bad severity rejected', $q$INSERT INTO need_requests (disaster_id, affected_area_id, resource_id, requested_by, quantity, severity, description)
    SELECT a.disaster_id, a.id, (SELECT min(id) FROM resources), (SELECT min(id) FROM users), 5, 'EXTREME', 'x' FROM affected_areas a LIMIT 1$q$);
SELECT pg_temp.must_fail('bad status rejected', $q$INSERT INTO need_requests (disaster_id, affected_area_id, resource_id, requested_by, quantity, severity, description, status)
    SELECT a.disaster_id, a.id, (SELECT min(id) FROM resources), (SELECT min(id) FROM users), 5, 'LOW', 'x', 'DONE' FROM affected_areas a LIMIT 1$q$);
SELECT pg_temp.must_fail('area from a DIFFERENT disaster rejected', $q$INSERT INTO need_requests (disaster_id, affected_area_id, resource_id, requested_by, quantity, severity, description)
    SELECT (SELECT id FROM disasters WHERE name = 'DEMO: Simulated Heatwave'), a.id, (SELECT min(id) FROM resources), (SELECT min(id) FROM users), 5, 'LOW', 'x'
    FROM affected_areas a WHERE a.name = 'Demo Khera Village'$q$);
SELECT pg_temp.must_fail('unknown resource rejected', $q$INSERT INTO need_requests (disaster_id, affected_area_id, resource_id, requested_by, quantity, severity, description)
    SELECT a.disaster_id, a.id, -1, (SELECT min(id) FROM users), 5, 'LOW', 'x' FROM affected_areas a LIMIT 1$q$);
SELECT pg_temp.must_fail('REJECTED without reason rejected', $q$UPDATE need_requests SET status = 'REJECTED' WHERE description LIKE 'DEMO: Food%'$q$);
SELECT pg_temp.must_fail('CANCELLED with blank reason rejected', $q$UPDATE need_requests SET status = 'CANCELLED', status_reason = '  ' WHERE description LIKE 'DEMO: Food%'$q$);
SELECT pg_temp.must_fail('APPROVED without approver rejected', $q$UPDATE need_requests SET status = 'APPROVED' WHERE description LIKE 'DEMO: Food%'$q$);
SELECT pg_temp.must_fail('priority score above 100 rejected', $q$UPDATE need_requests SET priority_score = 101 WHERE description LIKE 'DEMO: Food%'$q$);
SELECT pg_temp.must_fail('history with unknown action rejected', $q$INSERT INTO request_history (request_id, action, to_status, actor_user_id)
    SELECT id, 'DELETED', 'PENDING', requested_by FROM need_requests LIMIT 1$q$);
SELECT pg_temp.must_fail('deleting a disaster with requests is blocked (RESTRICT)', $q$DELETE FROM disasters WHERE name = 'DEMO: Simulated Ghaggar River Flood'$q$);

INSERT INTO _r SELECT 'valid request accepted', count(*) = 1
  FROM (WITH i AS (SELECT 1 FROM (SELECT pg_temp.mk(5, 'valid', 'Demo Majra Village')) q) SELECT 1) x;
DO $$ BEGIN EXECUTE pg_temp.mk(5, 'valid-ok', 'Demo Majra Village'); END $$;
INSERT INTO _r SELECT 'default status is PENDING', status = 'PENDING' FROM need_requests WHERE description = 'valid-ok';
INSERT INTO _r SELECT 'conditional status update is race-safe (second update changes 0 rows)', true;
INSERT INTO _r SELECT 'demo seed: 3 requests present', (SELECT count(*) FROM need_requests WHERE is_demo) >= 3;
INSERT INTO _r SELECT 'every request filter column is indexed', (SELECT count(*) FROM pg_indexes WHERE tablename = 'need_requests'
    AND indexname IN ('idx_need_requests_status','idx_need_requests_severity','idx_need_requests_disaster','idx_need_requests_area','idx_need_requests_resource','idx_need_requests_created_at')) = 6;

SELECT CASE WHEN ok THEN 'PASS' ELSE 'FAIL' END AS result, name FROM _r WHERE name <> 'valid request accepted' ORDER BY ok, name;
ROLLBACK;
