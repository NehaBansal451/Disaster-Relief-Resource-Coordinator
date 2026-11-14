-- Fictional shelter demo data for the Spring dev profile only.
-- The zz-prefixed Flyway repeatable description sorts after R__dev_seed and
-- R__z_dev_inventory_seed. Every insert is idempotent.
-- Never use these demo records in production.

WITH shelter_seed AS (
    SELECT * FROM (VALUES
        ('SH-PTL-01', 'DEMO: Patiala Community Relief Centre', 'Patiala', 'Punjab',
         'Demo address, Patiala', '+91-00000-02001',
         250, 118, 'OPEN', TRUE, TRUE, TRUE, TRUE,
         'Registration desk, accessible washrooms and first-aid room.',
         76.3860::DOUBLE PRECISION, 30.3410::DOUBLE PRECISION,
         'warehouse1@demo.drrc.local'),
        ('SH-RPR-01', 'DEMO: Rajpura School Shelter', 'Patiala', 'Punjab',
         'Demo address, Rajpura', '+91-00000-02002',
         120, 120, 'OPEN', TRUE, TRUE, FALSE, TRUE,
         'School building with separate family rooms.',
         76.5940::DOUBLE PRECISION, 30.4840::DOUBLE PRECISION,
         'warehouse2@demo.drrc.local'),
        ('SH-SMN-01', 'DEMO: Samana Relief Camp', 'Patiala', 'Punjab',
         'Demo address, Samana', '+91-00000-02003',
         80, 92, 'OPEN', TRUE, TRUE, TRUE, FALSE,
         'Temporary camp; occupancy is intentionally above planned capacity to demonstrate the overcrowding alert.',
         76.1900::DOUBLE PRECISION, 30.1500::DOUBLE PRECISION,
         'warehouse2@demo.drrc.local')
    ) AS s(code, name, district, state, address, contact_phone,
           capacity_people, current_occupancy, status,
           has_food_support, has_water_support, has_medical_support, is_accessible,
           facility_notes, longitude, latitude, manager_email)
)
INSERT INTO shelters (
    code, name, organization_id, manager_user_id,
    address, district, state, contact_phone,
    capacity_people, current_occupancy, status,
    has_food_support, has_water_support, has_medical_support, is_accessible,
    facility_notes, location, is_demo
)
SELECT
    s.code,
    s.name,
    org.id,
    manager.id,
    s.address,
    s.district,
    s.state,
    s.contact_phone,
    s.capacity_people,
    s.current_occupancy,
    s.status,
    s.has_food_support,
    s.has_water_support,
    s.has_medical_support,
    s.is_accessible,
    s.facility_notes,
    ST_SetSRID(ST_MakePoint(s.longitude, s.latitude), 4326)::geography,
    TRUE
FROM shelter_seed s
LEFT JOIN organizations org
    ON org.name = 'DEMO Punjab Disaster Response Cell'
LEFT JOIN users manager
    ON lower(manager.email) = lower(s.manager_email)
ON CONFLICT (code) DO NOTHING;
