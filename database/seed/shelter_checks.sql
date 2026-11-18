-- Shelter schema checks for V8, run after V1-V8 on PostgreSQL 16 + PostGIS 3.4.
-- Usage (from deployment/docker):
--   docker cp ..\..\database\queries\shelter_checks.sql drrc-db:/tmp/shelter_checks.sql
--   docker compose exec -T db psql -U <db-user> -d <db-name> -v ON_ERROR_STOP=1 -f /tmp/shelter_checks.sql
-- All fixtures are temporary to this transaction and are rolled back.

BEGIN;

CREATE TEMP TABLE _shelter_check_results (
    name TEXT NOT NULL,
    ok   BOOLEAN NOT NULL
) ON COMMIT DROP;

CREATE TEMP TABLE _shelter_fixture (
    organization_id BIGINT,
    manager_user_id BIGINT,
    shelter_id BIGINT,
    overcrowded_shelter_id BIGINT,
    unassigned_shelter_id BIGINT,
    closed_shelter_id BIGINT
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
        INSERT INTO _shelter_check_results VALUES (label, FALSE);
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS actual_state = RETURNED_SQLSTATE;
        INSERT INTO _shelter_check_results VALUES (label, actual_state = expected_state);
    END;
END;
$$;

DO $$
DECLARE
    suffix TEXT := pg_backend_pid()::TEXT;
    v_org BIGINT;
    v_manager BIGINT;
    v_shelter BIGINT;
    v_overcrowded BIGINT;
    v_unassigned BIGINT;
    v_closed BIGINT;
BEGIN
    INSERT INTO organizations (name, type, is_demo)
    VALUES ('Shelter Check Org ' || suffix, 'OTHER', TRUE)
    RETURNING id INTO v_org;

    INSERT INTO users (full_name, email, password_hash, role, enabled, preferred_language, is_demo)
    VALUES ('Shelter Check Manager', 'shelter-manager-' || suffix || '@test.local',
            'test-hash', 'WAREHOUSE_MANAGER', TRUE, 'en', TRUE)
    RETURNING id INTO v_manager;

    INSERT INTO shelters (
        code, name, organization_id, manager_user_id,
        capacity_people, current_occupancy, status,
        has_food_support, has_water_support, has_medical_support, is_accessible,
        facility_notes, location
    ) VALUES (
        'SH-CHECK-' || suffix, 'Shelter Check Facility', v_org, v_manager,
        100, 40, 'OPEN', TRUE, TRUE, FALSE, TRUE,
        'Registration point and accessible washrooms.',
        ST_SetSRID(ST_MakePoint(76.3869, 30.3398), 4326)::geography
    ) RETURNING id INTO v_shelter;

    -- Over-capacity counts are valid reports and must be visible as overcrowding.
    INSERT INTO shelters (
        code, name, capacity_people, current_occupancy, status, location
    ) VALUES (
        'SH-OVER-' || suffix, 'Overcrowded Shelter Check', 10, 12, 'OPEN',
        ST_SetSRID(ST_MakePoint(76.3870, 30.3400), 4326)::geography
    ) RETURNING id INTO v_overcrowded;

    -- Organization and manager are optional for a shelter in the initial model.
    INSERT INTO shelters (code, name, capacity_people, location)
    VALUES (
        'SH-UNASSIGNED-' || suffix, 'Unassigned Shelter Check', 20,
        ST_SetSRID(ST_MakePoint(76.3880, 30.3410), 4326)::geography
    ) RETURNING id INTO v_unassigned;

    INSERT INTO shelters (code, name, capacity_people, current_occupancy, status, location)
    VALUES (
        'SH-CLOSED-' || suffix, 'Closed Shelter Check', 30, 5, 'CLOSED',
        ST_SetSRID(ST_MakePoint(76.3890, 30.3420), 4326)::geography
    ) RETURNING id INTO v_closed;

    INSERT INTO _shelter_fixture
    VALUES (v_org, v_manager, v_shelter, v_overcrowded, v_unassigned, v_closed);
END;
$$;

INSERT INTO _shelter_check_results
SELECT 'valid shelter with organization and manager is accepted',
       s.organization_id = f.organization_id
       AND s.manager_user_id = f.manager_user_id
       AND s.capacity_people = 100
       AND s.current_occupancy = 40
  FROM shelters s CROSS JOIN _shelter_fixture f
 WHERE s.id = f.shelter_id;

INSERT INTO _shelter_check_results
SELECT 'organization and manager may be omitted',
       s.organization_id IS NULL AND s.manager_user_id IS NULL
  FROM shelters s CROSS JOIN _shelter_fixture f
 WHERE s.id = f.unassigned_shelter_id;

INSERT INTO _shelter_check_results
SELECT 'capacity summary derives available places correctly',
       v.available_capacity = 60 AND NOT v.is_full AND NOT v.is_overcrowded
  FROM shelter_capacity_summary v CROSS JOIN _shelter_fixture f
 WHERE v.id = f.shelter_id;

INSERT INTO _shelter_check_results
SELECT 'over-capacity occupancy is stored and flagged',
       v.available_capacity = 0 AND v.is_full AND v.is_overcrowded
  FROM shelter_capacity_summary v CROSS JOIN _shelter_fixture f
 WHERE v.id = f.overcrowded_shelter_id;

INSERT INTO _shelter_check_results
SELECT 'closed shelter has no operationally available capacity',
       v.available_capacity = 0 AND NOT v.is_full
  FROM shelter_capacity_summary v CROSS JOIN _shelter_fixture f
 WHERE v.id = f.closed_shelter_id;

SELECT pg_temp.expect_sqlstate(
    'blank shelter code is rejected',
    format($q$INSERT INTO shelters (code, name, capacity_people, location)
           VALUES ('   ', 'Bad code', 10,
                   ST_SetSRID(ST_MakePoint(76.38, 30.34),4326)::geography)$q$),
    '23514'
);

SELECT pg_temp.expect_sqlstate(
    'blank shelter name is rejected',
    format($q$INSERT INTO shelters (code, name, capacity_people, location)
           VALUES ('SH-BAD-NAME-' || pg_backend_pid(), '   ', 10,
                   ST_SetSRID(ST_MakePoint(76.38, 30.34),4326)::geography)$q$),
    '23514'
);

SELECT pg_temp.expect_sqlstate(
    'duplicate shelter code is rejected',
    format($q$INSERT INTO shelters (code, name, capacity_people, location)
           SELECT s.code, 'Duplicate Code', 10, s.location
             FROM shelters s JOIN _shelter_fixture f ON s.id = f.shelter_id$q$),
    '23505'
);

SELECT pg_temp.expect_sqlstate(
    'zero shelter capacity is rejected',
    format($q$INSERT INTO shelters (code, name, capacity_people, location)
           VALUES ('SH-ZERO-' || pg_backend_pid(), 'Zero Capacity', 0,
                   ST_SetSRID(ST_MakePoint(76.38, 30.34),4326)::geography)$q$),
    '23514'
);

SELECT pg_temp.expect_sqlstate(
    'negative shelter capacity is rejected',
    format($q$INSERT INTO shelters (code, name, capacity_people, location)
           VALUES ('SH-NEG-CAP-' || pg_backend_pid(), 'Negative Capacity', -1,
                   ST_SetSRID(ST_MakePoint(76.38, 30.34),4326)::geography)$q$),
    '23514'
);

SELECT pg_temp.expect_sqlstate(
    'negative occupancy is rejected',
    format($q$INSERT INTO shelters (code, name, capacity_people, current_occupancy, location)
           VALUES ('SH-NEG-' || pg_backend_pid(), 'Negative Occupancy', 10, -1,
                   ST_SetSRID(ST_MakePoint(76.38, 30.34),4326)::geography)$q$),
    '23514'
);

SELECT pg_temp.expect_sqlstate(
    'invalid shelter status is rejected',
    format($q$UPDATE shelters SET status = 'FULL'
           WHERE id = (SELECT shelter_id FROM _shelter_fixture)$q$),
    '23514'
);

SELECT pg_temp.expect_sqlstate(
    'blank contact phone is rejected',
    format($q$UPDATE shelters SET contact_phone = '   '
           WHERE id = (SELECT shelter_id FROM _shelter_fixture)$q$),
    '23514'
);

SELECT pg_temp.expect_sqlstate(
    'missing shelter location is rejected',
    format($q$INSERT INTO shelters (code, name, capacity_people)
           VALUES ('SH-NO-LOCATION-' || pg_backend_pid(), 'Missing Location', 10)$q$),
    '23502'
);

SELECT pg_temp.expect_sqlstate(
    'organization deletion is restricted while shelter references it',
    format($q$DELETE FROM organizations
           WHERE id = (SELECT organization_id FROM _shelter_fixture)$q$),
    '23503'
);

SELECT pg_temp.expect_sqlstate(
    'manager deletion is restricted while shelter references the user',
    format($q$DELETE FROM users
           WHERE id = (SELECT manager_user_id FROM _shelter_fixture)$q$),
    '23503'
);

INSERT INTO _shelter_check_results
SELECT 'shelter location has expected point geometry and SRID',
       GeometryType(s.location::geometry) = 'POINT'
       AND ST_SRID(s.location::geometry) = 4326
  FROM shelters s CROSS JOIN _shelter_fixture f
 WHERE s.id = f.shelter_id;

INSERT INTO _shelter_check_results
SELECT 'shelter location GiST index exists',
       EXISTS (
           SELECT 1 FROM pg_indexes
            WHERE schemaname = 'public' AND indexname = 'gix_shelters_location'
       );

INSERT INTO _shelter_check_results
SELECT 'shelter status/occupancy indexes exist',
       EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_shelters_status')
       AND EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_shelters_district_state')
       AND EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_shelters_organization')
       AND EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_shelters_manager')
       AND EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_shelters_occupancy');

INSERT INTO _shelter_check_results
SELECT 'capacity summary view exists',
       to_regclass('public.shelter_capacity_summary') IS NOT NULL;

SELECT CASE WHEN ok THEN 'PASS' ELSE 'FAIL' END AS result, name
FROM _shelter_check_results
ORDER BY ok, name;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM _shelter_check_results WHERE NOT ok) THEN
        RAISE EXCEPTION 'Shelter checks FAILED';
    END IF;
END;
$$;

ROLLBACK;
