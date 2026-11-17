-- Day 5 demo data (fictional). Idempotent. Runs only under the dev profile, after R__dev_seed.sql.
INSERT INTO inventory (warehouse_id, resource_id, batch_ref, quantity_on_hand, quantity_reserved, expiry_date)
SELECT w.id, r.id, 'DEMO-' || w.code, s.qty, 0,
       CASE WHEN r.perishable THEN current_date + s.days ELSE NULL END
FROM (VALUES
  ('PTL-01', 'Drinking water',          20000, 150),
  ('PTL-01', 'Packaged food ration',     8000,  60),
  ('PTL-01', 'First-aid kit',             300, 400),
  ('PTL-01', 'Blanket',                  1500, NULL),
  ('PTL-01', 'Family tent',               120, NULL),
  ('RPR-01', 'Drinking water',           6000,  25),
  ('RPR-01', 'Packaged food ration',     2500,  20),
  ('RPR-01', 'Life jacket',               200, NULL),
  ('SMN-01', 'Drinking water',           1500, 120),
  ('SMN-01', 'Essential medicines pack',  150, 200)
) AS s(code, res, qty, days)
JOIN warehouses w ON w.code = s.code
JOIN resources r ON lower(r.name) = lower(s.res)
ON CONFLICT (warehouse_id, resource_id, batch_ref) DO NOTHING;

INSERT INTO inventory_transactions (inventory_id, warehouse_id, resource_id, type, quantity, on_hand_after, reserved_after,
                                    reference_type, note, actor_user_id)
SELECT i.id, i.warehouse_id, i.resource_id, 'RECEIPT', i.quantity_on_hand, i.quantity_on_hand, 0, 'SEED', 'DEMO opening stock',
       (SELECT id FROM users WHERE lower(email) = 'officer@demo.drrc.local')
FROM inventory i
WHERE i.batch_ref LIKE 'DEMO-%'
  AND NOT EXISTS (SELECT 1 FROM inventory_transactions t WHERE t.inventory_id = i.id);

INSERT INTO shelters (disaster_id, name, address, district, state, capacity, occupancy, facilities, accessible, status, location, is_demo)
SELECT (SELECT id FROM disasters WHERE name = 'DEMO: Simulated Ghaggar River Flood'),
       v.name, v.addr, 'Patiala', 'Punjab', v.cap, v.occ, v.fac, v.acc, 'OPEN',
       ST_SetSRID(ST_MakePoint(v.lon, v.lat), 4326)::geography, TRUE
FROM (VALUES
  ('DEMO Govt School Shelter, Patiala',  'Demo address, Patiala', 400, 380, 'Water, toilets, kitchen, first-aid corner', TRUE,  76.3900, 30.3300),
  ('DEMO Community Hall Shelter, Rajpura','Demo address, Rajpura', 250, 310, 'Water, toilets',                           FALSE, 76.5900, 30.4800),
  ('DEMO Gurdwara Shelter, Samana',       'Demo address, Samana',  600, 120, 'Water, toilets, langar kitchen, medical',  TRUE,  76.1950, 30.1550)
) AS v(name, addr, cap, occ, fac, acc, lon, lat)
WHERE NOT EXISTS (SELECT 1 FROM shelters s WHERE s.name = v.name);
