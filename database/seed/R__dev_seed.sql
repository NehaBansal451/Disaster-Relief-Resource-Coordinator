-- DEVELOPMENT / DEMO SEED DATA (PROPOSED D5, pending Divay approval).
-- All people, organisations, villages and depots below are FICTIONAL and marked is_demo = TRUE
-- (except the resource catalogue, which is reference data: see open question in the Day 2 report).
-- Flyway "repeatable" migration: re-runs whenever this file changes, so every statement is idempotent.
-- Loaded only with the Spring profile "dev" (spring.flyway.locations includes classpath:db/seed).
-- Demo password for every demo user: Demo@12345  (BCrypt hash below). NEVER use in production.

INSERT INTO organizations (name, type, contact_email, contact_phone, is_demo) VALUES
  ('DEMO Punjab Disaster Response Cell', 'GOVERNMENT', 'cell@demo.drrc.local',  '+91-00000-00001', TRUE),
  ('DEMO Jal Seva Relief Trust',         'NGO',        'trust@demo.drrc.local', '+91-00000-00002', TRUE),
  ('DEMO Rural Health Volunteers',       'NGO',        'health@demo.drrc.local','+91-00000-00003', TRUE)
ON CONFLICT (name) DO NOTHING;

INSERT INTO users (full_name, email, password_hash, role, organization_id, preferred_language, is_demo)
SELECT v.full_name, v.email, '$2b$10$HfpIlzbvjV6QB8AjzTmmgu6O/Y3j3g6UHE9IshwRPkQciK70KMjrW', v.role,
       (SELECT id FROM organizations WHERE name = v.org), v.lang, TRUE
FROM (VALUES
  ('Demo Admin',            'admin@demo.drrc.local',    'ADMIN',             'DEMO Punjab Disaster Response Cell', 'en'),
  ('Demo Relief Officer',   'officer@demo.drrc.local',  'OFFICER',           'DEMO Punjab Disaster Response Cell', 'en'),
  ('Demo Warehouse Mgr A',  'warehouse1@demo.drrc.local','WAREHOUSE_MANAGER','DEMO Punjab Disaster Response Cell', 'pa'),
  ('Demo Warehouse Mgr B',  'warehouse2@demo.drrc.local','WAREHOUSE_MANAGER','DEMO Punjab Disaster Response Cell', 'hi'),
  ('Demo Field Worker',     'field@demo.drrc.local',    'FIELD_WORKER',      'DEMO Punjab Disaster Response Cell', 'pa'),
  ('Demo NGO Coordinator',  'ngo@demo.drrc.local',      'NGO',               'DEMO Jal Seva Relief Trust',         'en'),
  ('Demo Volunteer',        'volunteer@demo.drrc.local','VOLUNTEER',         'DEMO Rural Health Volunteers',       'hi'),
  ('Demo Citizen Reporter', 'citizen@demo.drrc.local',  'CITIZEN',           NULL,                                 'en')
) AS v(full_name, email, role, org, lang)
ON CONFLICT ((lower(email))) DO NOTHING;

INSERT INTO disasters (name, disaster_type, severity, status, started_at, ended_at, description, epicentre, created_by, is_demo)
SELECT 'DEMO: Simulated Ghaggar River Flood', 'FLOOD', 'HIGH', 'ACTIVE',
       TIMESTAMPTZ '2026-11-03 06:00:00+05:30', NULL,
       'Simulated flood for demonstration. Not a real event.',
       ST_SetSRID(ST_MakePoint(76.1000, 30.2800), 4326)::geography,
       (SELECT id FROM users WHERE lower(email) = 'officer@demo.drrc.local'), TRUE
WHERE NOT EXISTS (SELECT 1 FROM disasters WHERE name = 'DEMO: Simulated Ghaggar River Flood');

INSERT INTO disasters (name, disaster_type, severity, status, started_at, ended_at, description, epicentre, created_by, is_demo)
SELECT 'DEMO: Simulated Heatwave', 'HEATWAVE', 'MEDIUM', 'CLOSED',
       TIMESTAMPTZ '2026-06-10 00:00:00+05:30', TIMESTAMPTZ '2026-06-25 00:00:00+05:30',
       'Closed simulated event, kept to show the disaster lifecycle. Not a real event.',
       ST_SetSRID(ST_MakePoint(76.3869, 30.3398), 4326)::geography,
       (SELECT id FROM users WHERE lower(email) = 'officer@demo.drrc.local'), TRUE
WHERE NOT EXISTS (SELECT 1 FROM disasters WHERE name = 'DEMO: Simulated Heatwave');

INSERT INTO affected_areas (disaster_id, name, area_type, district, state, population_estimate, impact_level, impact_description, location)
SELECT d.id, a.name, 'VILLAGE', 'Patiala', 'Punjab', a.pop, a.impact, a.descr,
       ST_SetSRID(ST_MakePoint(a.lon, a.lat), 4326)::geography
FROM disasters d,
     (VALUES
       ('Demo Khera Village', 1800, 'CRITICAL', 'Fields and lanes under about 1.5 m of water; no drinking water.', 76.1500, 30.2850),
       ('Demo Majra Village', 1500, 'HIGH',     'Road access cut; food stock exhausted.',                          76.1050, 30.2400),
       ('Demo Kalan Village',  900, 'MEDIUM',   'Partial flooding; clinic needs first-aid supplies.',              76.0700, 30.3300)
     ) AS a(name, pop, impact, descr, lon, lat)
WHERE d.name = 'DEMO: Simulated Ghaggar River Flood'
ON CONFLICT ON CONSTRAINT uq_affected_areas_disaster_name DO NOTHING;

INSERT INTO resources (name, category, unit, perishable, shelf_life_days, handling_notes) VALUES
  ('Drinking water',          'WATER',            'litre',   TRUE,  180, 'Sealed bottles or cans; keep out of direct sun.'),
  ('Packaged food ration',    'FOOD',             'packet',  TRUE,   90, 'One packet feeds one person for one day.'),
  ('First-aid kit',           'MEDICAL',          'kit',     TRUE,  730, 'Check seal and expiry before dispatch.'),
  ('Essential medicines pack','MEDICAL',          'pack',    TRUE,  365, 'Store below 25 C.'),
  ('Blanket',                 'BEDDING',          'piece',   FALSE, NULL, NULL),
  ('Family tent',             'SHELTER',          'piece',   FALSE, NULL, 'Sleeps five.'),
  ('Life jacket',             'RESCUE_EQUIPMENT', 'piece',   FALSE, NULL, NULL),
  ('Light truck',             'VEHICLE',          'vehicle', FALSE, NULL, 'Needs a driver and fuel record.')
ON CONFLICT ((lower(name))) DO NOTHING;

INSERT INTO warehouses (code, name, organization_id, manager_user_id, address, district, state, contact_phone, capacity_m3, status, location, is_demo)
SELECT w.code, w.name,
       (SELECT id FROM organizations WHERE name = 'DEMO Punjab Disaster Response Cell'),
       (SELECT id FROM users WHERE lower(email) = w.mgr),
       w.addr, 'Patiala', 'Punjab', '+91-00000-0010', w.cap, 'ACTIVE',
       ST_SetSRID(ST_MakePoint(w.lon, w.lat), 4326)::geography, TRUE
FROM (VALUES
  ('PTL-01', 'DEMO Patiala Central Depot', 'warehouse1@demo.drrc.local', 'Demo address, Patiala',  1200.00, 76.3869, 30.3398),
  ('RPR-01', 'DEMO Rajpura Depot',         'warehouse2@demo.drrc.local', 'Demo address, Rajpura',   800.00, 76.5940, 30.4840),
  ('SMN-01', 'DEMO Samana Depot',          'warehouse2@demo.drrc.local', 'Demo address, Samana',    500.00, 76.1900, 30.1500)
) AS w(code, name, mgr, addr, cap, lon, lat)
ON CONFLICT (code) DO NOTHING;

-- Day 4: demo emergency requests (fictional). Idempotent: skipped when the same description already exists.
INSERT INTO need_requests (disaster_id, affected_area_id, resource_id, requested_by, quantity, affected_population,
                           severity, description, status, approved_by, approved_at, is_demo)
SELECT a.disaster_id, a.id, r.id, u.id, v.qty, v.pop, v.sev, v.descr, v.st, NULL, NULL, TRUE
FROM (VALUES
  ('Demo Khera Village', 'Drinking water',        5000, 1800, 'CRITICAL', 'DEMO: No drinking water for 1,800 people since the flood.', 'PENDING',  'field@demo.drrc.local'),
  ('Demo Kalan Village', 'First-aid kit',           40,  900, 'MEDIUM',   'DEMO: Village clinic has run out of first-aid kits.',       'PENDING',  'field@demo.drrc.local'),
  ('Demo Majra Village', 'Packaged food ration',  1500, 1500, 'HIGH',     'DEMO: Food stocks exhausted; road access is cut.',          'VERIFIED', 'ngo@demo.drrc.local')
) AS v(area, res, qty, pop, sev, descr, st, requester)
JOIN affected_areas a ON a.name = v.area
JOIN resources r ON lower(r.name) = lower(v.res)
JOIN users u ON lower(u.email) = v.requester
WHERE NOT EXISTS (SELECT 1 FROM need_requests n WHERE n.description = v.descr);

INSERT INTO request_history (request_id, action, from_status, to_status, actor_user_id, reason)
SELECT n.id, 'CREATED', NULL, 'PENDING', n.requested_by, NULL
FROM need_requests n
WHERE n.is_demo AND NOT EXISTS (SELECT 1 FROM request_history h WHERE h.request_id = n.id AND h.action = 'CREATED');

INSERT INTO request_history (request_id, action, from_status, to_status, actor_user_id, reason)
SELECT n.id, 'VERIFIED', 'PENDING', 'VERIFIED', (SELECT id FROM users WHERE lower(email) = 'officer@demo.drrc.local'), NULL
FROM need_requests n
WHERE n.is_demo AND n.status = 'VERIFIED'
  AND NOT EXISTS (SELECT 1 FROM request_history h WHERE h.request_id = n.id AND h.action = 'VERIFIED');

