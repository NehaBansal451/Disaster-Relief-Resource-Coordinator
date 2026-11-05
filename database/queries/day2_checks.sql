-- Day 2 database self-check. Run AFTER migrations. Changes nothing: all test rows are rolled back.
-- Prints PASS/FAIL per check and raises an error at the end if anything failed.
-- Usage: psql -v ON_ERROR_STOP=1 -d <db> -f database/queries/day2_checks.sql
BEGIN;
CREATE TEMP TABLE check_results (name text, ok boolean) ON COMMIT DROP;

-- expect_error(label, statement): statement must FAIL with an integrity error.
CREATE FUNCTION pg_temp.expect_error(label text, stmt text) RETURNS void LANGUAGE plpgsql AS $f$
DECLARE passed boolean := false;
BEGIN
  BEGIN
    EXECUTE stmt;
    RAISE EXCEPTION 'statement unexpectedly succeeded';          -- undoes the statement's effect
  EXCEPTION
    WHEN integrity_constraint_violation OR check_violation THEN passed := true;   -- the database refused it: good
    WHEN raise_exception THEN passed := false;                                    -- it succeeded: bad
  END;
  INSERT INTO check_results VALUES (label, passed);
END $f$;
-- expect_ok(label, boolean query)
CREATE FUNCTION pg_temp.expect_true(label text, q text) RETURNS void LANGUAGE plpgsql AS $f$
DECLARE r boolean;
BEGIN EXECUTE q INTO r; INSERT INTO check_results VALUES (label, coalesce(r,false)); END $f$;

\o /dev/null
SELECT pg_temp.expect_true('tables exist (users, organizations, disasters, affected_areas, resources, warehouses)',
 $q$SELECT count(*)=6 FROM information_schema.tables WHERE table_schema='public'
    AND table_name IN ('users','organizations','disasters','affected_areas','resources','warehouses')$q$);
SELECT pg_temp.expect_true('PostGIS installed', $q$SELECT postgis_version() IS NOT NULL$q$);
SELECT pg_temp.expect_true('GiST spatial indexes exist (4)',
 $q$SELECT count(*)>=4 FROM pg_indexes WHERE schemaname='public' AND indexdef LIKE '%USING gist%'$q$);

-- users
SELECT pg_temp.expect_error('users: invalid role rejected',
 $q$INSERT INTO users(full_name,email,password_hash,role) VALUES('x','bad-role@t.local','h','HACKER')$q$);
SELECT pg_temp.expect_error('users: duplicate email differing only by case rejected',
 $q$INSERT INTO users(full_name,email,password_hash,role) VALUES('a','Dup@T.local','h','ADMIN'),('b','dup@t.LOCAL','h','ADMIN')$q$);
SELECT pg_temp.expect_error('users: invalid language rejected',
 $q$INSERT INTO users(full_name,email,password_hash,role,preferred_language) VALUES('a','lang@t.local','h','ADMIN','xx')$q$);

-- disasters
SELECT pg_temp.expect_error('disasters: invalid type rejected',
 $q$INSERT INTO disasters(name,disaster_type,severity,started_at) VALUES('d','ZOMBIES','HIGH',now())$q$);
SELECT pg_temp.expect_error('disasters: end before start rejected',
 $q$INSERT INTO disasters(name,disaster_type,severity,status,started_at,ended_at) VALUES('d','FLOOD','HIGH','ACTIVE',now(),now()-interval '1 day')$q$);
SELECT pg_temp.expect_error('disasters: CLOSED without end date rejected',
 $q$INSERT INTO disasters(name,disaster_type,severity,status,started_at) VALUES('d','FLOOD','HIGH','CLOSED',now())$q$);
SELECT pg_temp.expect_error('disasters: unknown creator rejected (FK)',
 $q$INSERT INTO disasters(name,disaster_type,severity,started_at,created_by) VALUES('d','FLOOD','HIGH',now(),999999999)$q$);

-- affected_areas
SELECT pg_temp.expect_error('affected_areas: unknown disaster rejected (FK)',
 $q$INSERT INTO affected_areas(disaster_id,name,population_estimate,impact_level,location) VALUES(999999999,'v',10,'HIGH',ST_SetSRID(ST_MakePoint(76,30),4326)::geography)$q$);
SELECT pg_temp.expect_error('affected_areas: negative population rejected',
 $q$WITH d AS (INSERT INTO disasters(name,disaster_type,severity,started_at) VALUES('d','FLOOD','HIGH',now()) RETURNING id)
    INSERT INTO affected_areas(disaster_id,name,population_estimate,impact_level,location)
    SELECT id,'v',-5,'HIGH',ST_SetSRID(ST_MakePoint(76,30),4326)::geography FROM d$q$);
SELECT pg_temp.expect_error('affected_areas: same name twice in one disaster (NULL district) rejected',
 $q$WITH d AS (INSERT INTO disasters(name,disaster_type,severity,started_at) VALUES('d','FLOOD','HIGH',now()) RETURNING id)
    INSERT INTO affected_areas(disaster_id,name,population_estimate,impact_level,location)
    SELECT id,'v',5,'HIGH',ST_SetSRID(ST_MakePoint(76,30),4326)::geography FROM d
    UNION ALL SELECT id,'v',5,'HIGH',ST_SetSRID(ST_MakePoint(76,30),4326)::geography FROM d$q$);
SELECT pg_temp.expect_error('disasters: deleting a disaster that has areas is blocked (RESTRICT)',
 $q$DO $d$ DECLARE did bigint; BEGIN
      INSERT INTO disasters(name,disaster_type,severity,started_at) VALUES('dd','FLOOD','HIGH',now()) RETURNING id INTO did;
      INSERT INTO affected_areas(disaster_id,name,population_estimate,impact_level,location)
        VALUES (did,'v',5,'HIGH',ST_SetSRID(ST_MakePoint(76,30),4326)::geography);
      DELETE FROM disasters WHERE id = did;
    END $d$$q$);

-- resources
SELECT pg_temp.expect_error('resources: perishable without shelf life rejected',
 $q$INSERT INTO resources(name,category,unit,perishable) VALUES('r1','FOOD','kg',TRUE)$q$);
SELECT pg_temp.expect_error('resources: shelf life on non-perishable rejected',
 $q$INSERT INTO resources(name,category,unit,perishable,shelf_life_days) VALUES('r2','FOOD','kg',FALSE,30)$q$);
SELECT pg_temp.expect_error('resources: duplicate name ignoring case rejected',
 $q$INSERT INTO resources(name,category,unit) VALUES('Tarp','SHELTER','piece'),('TARP','SHELTER','piece')$q$);
SELECT pg_temp.expect_error('resources: blank unit rejected',
 $q$INSERT INTO resources(name,category,unit) VALUES('r3','FOOD','   ')$q$);

-- warehouses
SELECT pg_temp.expect_error('warehouses: duplicate code rejected',
 $q$INSERT INTO warehouses(code,name,location) VALUES('ZZ-1','a',ST_SetSRID(ST_MakePoint(76,30),4326)::geography),('ZZ-1','b',ST_SetSRID(ST_MakePoint(76,30),4326)::geography)$q$);
SELECT pg_temp.expect_error('warehouses: missing location rejected (NOT NULL)',
 $q$INSERT INTO warehouses(code,name) VALUES('ZZ-2','a')$q$);
SELECT pg_temp.expect_error('warehouses: zero capacity rejected',
 $q$INSERT INTO warehouses(code,name,capacity_m3,location) VALUES('ZZ-3','a',0,ST_SetSRID(ST_MakePoint(76,30),4326)::geography)$q$);

-- positive path: valid rows are accepted
SELECT pg_temp.expect_true('valid FIELD_WORKER user accepted',
 $q$WITH i AS (INSERT INTO users(full_name,email,password_hash,role) VALUES('ok','ok-fw@t.local','h','FIELD_WORKER') RETURNING 1) SELECT count(*)=1 FROM i$q$);

\o
SELECT CASE WHEN ok THEN 'PASS' ELSE 'FAIL' END AS result, name FROM check_results ORDER BY ok, name;
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM check_results WHERE NOT ok) THEN RAISE EXCEPTION 'Day 2 checks FAILED'; END IF;
END $$;
ROLLBACK;
