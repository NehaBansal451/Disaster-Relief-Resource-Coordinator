-- Fictional opening stock for local/demo use only.
-- Loaded after R__dev_seed.sql (z-prefixed description sorts after the base demo seed).
-- Idempotent: a receipt ledger row is created only when its inventory batch row is first created.

WITH seed_stock AS (
    SELECT * FROM (VALUES
        ('PTL-01', 'Drinking water',           'PTL-WATER-2026-01', DATE '2027-06-30', 3200, 'warehouse1@demo.drrc.local'),
        ('PTL-01', 'Packaged food ration',     'PTL-FOOD-2026-01',  DATE '2027-03-31',  900, 'warehouse1@demo.drrc.local'),
        ('PTL-01', 'First-aid kit',            'PTL-MED-2026-01',   DATE '2028-12-31',   30, 'warehouse1@demo.drrc.local'),
        ('PTL-01', 'Blanket',                  'PTL-BLANKET-2026-01', NULL::date,      200, 'warehouse1@demo.drrc.local'),
        ('RPR-01', 'Drinking water',           'RPR-WATER-2026-01', DATE '2027-08-31', 2200, 'warehouse2@demo.drrc.local'),
        ('RPR-01', 'Packaged food ration',     'RPR-FOOD-2026-01',  DATE '2027-04-30',  700, 'warehouse2@demo.drrc.local'),
        ('RPR-01', 'First-aid kit',            'RPR-MED-2026-01',   DATE '2028-11-30',   25, 'warehouse2@demo.drrc.local'),
        ('RPR-01', 'Blanket',                  'RPR-BLANKET-2026-01', NULL::date,      150, 'warehouse2@demo.drrc.local'),
        ('SMN-01', 'Drinking water',           'SMN-WATER-2026-01', DATE '2027-07-31', 1100, 'warehouse2@demo.drrc.local'),
        ('SMN-01', 'Packaged food ration',     'SMN-FOOD-2026-01',  DATE '2027-05-31',  500, 'warehouse2@demo.drrc.local'),
        ('SMN-01', 'First-aid kit',            'SMN-MED-2026-01',   DATE '2028-10-31',   15, 'warehouse2@demo.drrc.local'),
        ('SMN-01', 'Blanket',                  'SMN-BLANKET-2026-01', NULL::date,      100, 'warehouse2@demo.drrc.local')
    ) AS s(warehouse_code, resource_name, batch_number, expiry_date, opening_quantity, actor_email)
), inserted_inventory AS (
    INSERT INTO inventory (warehouse_id, resource_id, batch_number, expiry_date, quantity_on_hand, is_demo)
    SELECT w.id, r.id, s.batch_number, s.expiry_date, s.opening_quantity, TRUE
      FROM seed_stock s
      JOIN warehouses w ON w.code = s.warehouse_code
      JOIN resources r ON lower(r.name) = lower(s.resource_name)
    ON CONFLICT (warehouse_id, resource_id, batch_number) DO NOTHING
    RETURNING id, warehouse_id, resource_id, batch_number
)
INSERT INTO inventory_transactions (
    inventory_id, transaction_type, quantity_delta_on_hand, quantity_delta_reserved,
    actor_user_id, reason, external_reference, is_demo
)
SELECT i.id,
       'RECEIPT',
       s.opening_quantity,
       0,
       u.id,
       'DEMO: Opening stock for the fictional DRRC demonstration environment.',
       'DEMO-OPENING-' || s.warehouse_code || '-' || upper(replace(s.resource_name, ' ', '-')),
       TRUE
  FROM inserted_inventory i
  JOIN warehouses w ON w.id = i.warehouse_id
  JOIN resources r ON r.id = i.resource_id
  JOIN seed_stock s
    ON s.warehouse_code = w.code
   AND lower(s.resource_name) = lower(r.name)
   AND s.batch_number = i.batch_number
  JOIN users u ON lower(u.email) = lower(s.actor_email);
