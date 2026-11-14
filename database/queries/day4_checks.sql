-- Corrected Day 4 self-check for V5 (need_requests, request_history).
-- Requires V1-V5 and the dev seed to have been applied.
-- All test rows/updates are rolled back. Each assertion is printed as PASS/FAIL.
-- Usage: psql -v ON_ERROR_STOP=1 -d <db> -f day4_checks_corrected.sql

BEGIN;
CREATE TEMP TABLE _r(name text PRIMARY KEY, ok boolean NOT NULL);

CREATE OR REPLACE FUNCTION pg_temp.must_fail(label text, stmt text)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  BEGIN
    EXECUTE stmt;
    INSERT INTO _r VALUES (label, false);
  EXCEPTION
    WHEN check_violation OR foreign_key_violation OR unique_violation OR not_null_violation THEN
      INSERT INTO _r VALUES (label, true);
  END;
END $$;

CREATE OR REPLACE FUNCTION pg_temp.mk(q int, descr text DEFAULT 'chk', area text DEFAULT 'Demo Khera Village')
RETURNS text LANGUAGE sql AS $$
  SELECT format($f$
    INSERT INTO need_requests
      (disaster_id, affected_area_id, resource_id, requested_by, quantity, severity, description)
    SELECT a.disaster_id, a.id, (SELECT min(id) FROM resources), (SELECT min(id) FROM users),
           %s, 'HIGH', %L
    FROM affected_areas a WHERE a.name = %L
  $f$, q, descr, area)
$$;

-- The tests below use known development seed rows. Fail clearly rather than
-- silently reporting false results when the dev seed has not been loaded.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM users)
     OR NOT EXISTS (SELECT 1 FROM resources)
     OR NOT EXISTS (SELECT 1 FROM affected_areas WHERE name = 'Demo Khera Village')
     OR NOT EXISTS (SELECT 1 FROM affected_areas WHERE name = 'Demo Majra Village')
     OR NOT EXISTS (SELECT 1 FROM disasters WHERE name = 'DEMO: Simulated Ghaggar River Flood')
     OR NOT EXISTS (SELECT 1 FROM disasters WHERE name = 'DEMO: Simulated Heatwave') THEN
    RAISE EXCEPTION 'Day 4 checks require V1-V5 and the development seed; required demo rows are missing';
  END IF;
END $$;

-- Invalid request data and broken relationships must be rejected.
SELECT pg_temp.must_fail('zero quantity rejected', pg_temp.mk(0));
SELECT pg_temp.must_fail('negative quantity rejected', pg_temp.mk(-5));
SELECT pg_temp.must_fail('blank description rejected', pg_temp.mk(5, '   '));
SELECT pg_temp.must_fail('description over 2000 rejected', pg_temp.mk(5, repeat('x', 2001)));

SELECT pg_temp.must_fail('bad severity rejected', $q$
  INSERT INTO need_requests (disaster_id, affected_area_id, resource_id, requested_by,
                             quantity, severity, description)
  SELECT a.disaster_id, a.id, (SELECT min(id) FROM resources), (SELECT min(id) FROM users),
         5, 'EXTREME', 'invalid severity'
  FROM affected_areas a LIMIT 1
$q$);

SELECT pg_temp.must_fail('bad status rejected', $q$
  INSERT INTO need_requests (disaster_id, affected_area_id, resource_id, requested_by,
                             quantity, severity, description, status)
  SELECT a.disaster_id, a.id, (SELECT min(id) FROM resources), (SELECT min(id) FROM users),
         5, 'LOW', 'invalid status', 'DONE'
  FROM affected_areas a LIMIT 1
$q$);

SELECT pg_temp.must_fail('area from a different disaster rejected', $q$
  INSERT INTO need_requests (disaster_id, affected_area_id, resource_id, requested_by,
                             quantity, severity, description)
  SELECT (SELECT id FROM disasters WHERE name = 'DEMO: Simulated Heatwave'),
         a.id, (SELECT min(id) FROM resources), (SELECT min(id) FROM users),
         5, 'LOW', 'mismatched disaster and area'
  FROM affected_areas a WHERE a.name = 'Demo Khera Village'
$q$);

SELECT pg_temp.must_fail('unknown resource rejected', $q$
  INSERT INTO need_requests (disaster_id, affected_area_id, resource_id, requested_by,
                             quantity, severity, description)
  SELECT a.disaster_id, a.id, -1, (SELECT min(id) FROM users), 5, 'LOW', 'unknown resource'
  FROM affected_areas a LIMIT 1
$q$);

SELECT pg_temp.must_fail('REJECTED without reason rejected', $q$
  UPDATE need_requests SET status = 'REJECTED'
  WHERE description LIKE 'DEMO: Food%'
$q$);
SELECT pg_temp.must_fail('CANCELLED with blank reason rejected', $q$
  UPDATE need_requests SET status = 'CANCELLED', status_reason = '  '
  WHERE description LIKE 'DEMO: Food%'
$q$);
SELECT pg_temp.must_fail('APPROVED without approver rejected', $q$
  UPDATE need_requests SET status = 'APPROVED'
  WHERE description LIKE 'DEMO: Food%'
$q$);
SELECT pg_temp.must_fail('priority score above 100 rejected', $q$
  UPDATE need_requests SET priority_score = 101
  WHERE description LIKE 'DEMO: Food%'
$q$);
SELECT pg_temp.must_fail('history with unknown action rejected', $q$
  INSERT INTO request_history (request_id, action, to_status, actor_user_id)
  SELECT id, 'DELETED', 'PENDING', requested_by FROM need_requests LIMIT 1
$q$);
SELECT pg_temp.must_fail('deleting a disaster with requests is blocked', $q$
  DELETE FROM disasters WHERE name = 'DEMO: Simulated Ghaggar River Flood'
$q$);

-- Positive path: execute the generated INSERT, verify its generated ID and default status,
-- then exercise the conditional-update pattern twice on the same row.
DO $$
DECLARE
  new_id bigint;
  default_is_pending boolean := false;
  first_rows integer := 0;
  second_rows integer := 0;
BEGIN
  EXECUTE pg_temp.mk(5, 'valid-ok', 'Demo Majra Village') || ' RETURNING id' INTO new_id;
  INSERT INTO _r VALUES ('valid request INSERT executed', new_id IS NOT NULL);

  IF new_id IS NOT NULL THEN
    SELECT status = 'PENDING' INTO default_is_pending
    FROM need_requests WHERE id = new_id;
    INSERT INTO _r VALUES ('new request defaults to PENDING', coalesce(default_is_pending, false));

    UPDATE need_requests SET status = 'VERIFIED'
    WHERE id = new_id AND status = 'PENDING';
    GET DIAGNOSTICS first_rows = ROW_COUNT;

    UPDATE need_requests SET status = 'VERIFIED'
    WHERE id = new_id AND status = 'PENDING';
    GET DIAGNOSTICS second_rows = ROW_COUNT;

    INSERT INTO _r VALUES (
      'conditional update accepts first transition and rejects stale second transition',
      first_rows = 1 AND second_rows = 0
    );
  ELSE
    INSERT INTO _r VALUES ('new request defaults to PENDING', false);
    INSERT INTO _r VALUES (
      'conditional update accepts first transition and rejects stale second transition', false
    );
  END IF;
END $$;

INSERT INTO _r VALUES (
  'development seed contains at least 3 demo requests',
  (SELECT count(*) >= 3 FROM need_requests WHERE is_demo)
);

INSERT INTO _r VALUES (
  'all expected request indexes exist',
  (SELECT count(*) = 6 FROM pg_indexes WHERE schemaname = 'public'
    AND tablename = 'need_requests'
    AND indexname IN (
      'idx_need_requests_status', 'idx_need_requests_severity',
      'idx_need_requests_disaster', 'idx_need_requests_area',
      'idx_need_requests_resource', 'idx_need_requests_created_at'
    ))
);

SELECT CASE WHEN ok THEN 'PASS' ELSE 'FAIL' END AS result, name
FROM _r ORDER BY ok, name;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM _r WHERE NOT ok) THEN
    RAISE EXCEPTION 'Day 4 checks FAILED';
  END IF;
END $$;

ROLLBACK;
