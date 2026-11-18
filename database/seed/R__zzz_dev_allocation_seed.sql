-- Development/demo allocations for the fictional Ghaggar flood scenario.
-- Runs after R__dev_seed.sql, R__z_dev_inventory_seed.sql and R__zz_dev_shelter_seed.sql.
-- Repeatable and idempotent. Never load in production.
--
-- Creates two additional verified requests so the demo can show:
--   * a fully allocated water request (later dispatched and shown IN_TRANSIT),
--   * a recommended-only medical allocation (no stock is reserved).
-- The existing verified food request is fully allocated across two warehouses.

-- Additional verified requests. Existing base-seed requests are left unchanged.
INSERT INTO need_requests (
    disaster_id, affected_area_id, resource_id, requested_by, quantity,
    affected_population, severity, description, status, is_demo
)
SELECT d.id, a.id, r.id, u.id, v.quantity, v.population, v.severity,
       v.description, 'VERIFIED', TRUE
FROM (VALUES
    ('Demo Khera Village', 'Drinking water', 2500, 1800, 'CRITICAL',
     'DEMO: Verified water requirement for the Khera flood response.'),
    ('Demo Kalan Village', 'First-aid kit', 40, 900, 'HIGH',
     'DEMO: Verified medical requirement for the Kalan flood response.')
) AS v(area_name, resource_name, quantity, population, severity, description)
JOIN disasters d ON d.name = 'DEMO: Simulated Ghaggar River Flood'
JOIN affected_areas a ON a.disaster_id = d.id AND a.name = v.area_name
JOIN resources r ON lower(r.name) = lower(v.resource_name)
JOIN users u ON lower(u.email) = 'field@demo.drrc.local'
WHERE NOT EXISTS (
    SELECT 1 FROM need_requests n WHERE n.description = v.description
);

-- Ensure the demo requests have explainable priority values.
UPDATE need_requests
SET priority_score = CASE
        WHEN description = 'DEMO: No drinking water for 1,800 people since the flood.' THEN 88.00
        WHEN description = 'DEMO: Food stocks exhausted; road access is cut.' THEN 82.00
        WHEN description = 'DEMO: Verified water requirement for the Khera flood response.' THEN 94.00
        WHEN description = 'DEMO: Verified medical requirement for the Kalan flood response.' THEN 72.00
        ELSE priority_score
    END,
    priority_category = CASE
        WHEN description IN (
            'DEMO: No drinking water for 1,800 people since the flood.',
            'DEMO: Food stocks exhausted; road access is cut.',
            'DEMO: Verified water requirement for the Khera flood response.'
        ) THEN 'CRITICAL'
        WHEN description = 'DEMO: Verified medical requirement for the Kalan flood response.' THEN 'HIGH'
        ELSE priority_category
    END,
    priority_reasons = CASE
        WHEN description = 'DEMO: No drinking water for 1,800 people since the flood.'
            THEN 'DEMO score: critical water shortage affecting 1,800 people.'
        WHEN description = 'DEMO: Food stocks exhausted; road access is cut.'
            THEN 'DEMO score: food shortage and disrupted road access.'
        WHEN description = 'DEMO: Verified water requirement for the Khera flood response.'
            THEN 'DEMO score: critical drinking-water requirement affecting 1,800 people.'
        WHEN description = 'DEMO: Verified medical requirement for the Kalan flood response.'
            THEN 'DEMO score: high-priority first-aid requirement affecting 900 people.'
        ELSE priority_reasons
    END,
    priority_calculated_at = COALESCE(priority_calculated_at, TIMESTAMPTZ '2026-11-18 10:00:00+05:30')
WHERE is_demo
  AND description IN (
      'DEMO: No drinking water for 1,800 people since the flood.',
      'DEMO: Food stocks exhausted; road access is cut.',
      'DEMO: Verified water requirement for the Khera flood response.',
      'DEMO: Verified medical requirement for the Kalan flood response.'
  );

-- Record the creation/verification history for the two additional requests.
INSERT INTO request_history (request_id, action, from_status, to_status, actor_user_id, reason)
SELECT n.id, 'CREATED', NULL, 'PENDING', n.requested_by, 'DEMO: Seeded verified request.'
FROM need_requests n
WHERE n.is_demo
  AND n.description IN (
      'DEMO: Verified water requirement for the Khera flood response.',
      'DEMO: Verified medical requirement for the Kalan flood response.'
  )
  AND NOT EXISTS (
      SELECT 1 FROM request_history h
      WHERE h.request_id = n.id AND h.action = 'CREATED'
  );

INSERT INTO request_history (request_id, action, from_status, to_status, actor_user_id, reason)
SELECT n.id, 'VERIFIED', 'PENDING', 'VERIFIED', officer.id, 'DEMO: Verified for the allocation walkthrough.'
FROM need_requests n
CROSS JOIN users officer
WHERE lower(officer.email) = 'officer@demo.drrc.local'
  AND n.is_demo
  AND n.description IN (
      'DEMO: Verified water requirement for the Khera flood response.',
      'DEMO: Verified medical requirement for the Kalan flood response.'
  )
  AND NOT EXISTS (
      SELECT 1 FROM request_history h
      WHERE h.request_id = n.id AND h.action = 'VERIFIED'
  );

-- Move the existing verified food request and the new water request into the
-- approved state. The medical request intentionally remains VERIFIED.
UPDATE need_requests n
SET status = 'APPROVED',
    approved_by = officer.id,
    approved_at = COALESCE(n.approved_at, TIMESTAMPTZ '2026-11-18 10:05:00+05:30')
FROM users officer
WHERE lower(officer.email) = 'officer@demo.drrc.local'
  AND n.is_demo
  AND n.status = 'VERIFIED'
  AND n.description IN (
      'DEMO: Food stocks exhausted; road access is cut.',
      'DEMO: Verified water requirement for the Khera flood response.'
  );

INSERT INTO request_history (request_id, action, from_status, to_status, actor_user_id, reason)
SELECT n.id, 'APPROVED', 'VERIFIED', 'APPROVED', officer.id, 'DEMO: Approved for seeded allocation.'
FROM need_requests n
CROSS JOIN users officer
WHERE lower(officer.email) = 'officer@demo.drrc.local'
  AND n.is_demo
  AND n.description IN (
      'DEMO: Food stocks exhausted; road access is cut.',
      'DEMO: Verified water requirement for the Khera flood response.'
  )
  AND NOT EXISTS (
      SELECT 1 FROM request_history h
      WHERE h.request_id = n.id AND h.action = 'APPROVED'
  );

-- Allocation headers. The food and water allocations are approved; the medical
-- allocation is recommendation-only and therefore does not reserve stock.
INSERT INTO allocations (
    request_id, status, requested_qty, allocated_qty, explanation, note,
    created_by, approved_by, approved_at
)
SELECT n.id, a.status, n.quantity, a.allocated_qty, a.explanation, a.note,
       officer.id,
       CASE WHEN a.status = 'APPROVED' THEN officer.id ELSE NULL END,
       CASE WHEN a.status = 'APPROVED' THEN TIMESTAMPTZ '2026-11-18 10:10:00+05:30' ELSE NULL END
FROM (VALUES
    ('DEMO: Food stocks exhausted; road access is cut.', 'APPROVED', 1500,
     'Full food request covered by stock from Patiala Central Depot and Rajpura Depot.',
     'DEMO: Full allocation for the completed-delivery walkthrough.'),
    ('DEMO: Verified water requirement for the Khera flood response.', 'APPROVED', 2500,
     'Full water request covered by stock at Patiala Central Depot.',
     'DEMO: Full allocation for the in-transit walkthrough.'),
    ('DEMO: Verified medical requirement for the Kalan flood response.', 'RECOMMENDED', 25,
     'Partial recommendation: available first-aid stock covers 25 of 40 kits.',
     'DEMO: Shows a 15-kit shortage; recommendation has not been approved.')
) AS a(request_description, status, allocated_qty, explanation, note)
JOIN need_requests n ON n.description = a.request_description
CROSS JOIN users officer
WHERE lower(officer.email) = 'officer@demo.drrc.local'
  AND NOT EXISTS (
      SELECT 1 FROM allocations x
      WHERE x.request_id = n.id AND x.status IN ('RECOMMENDED', 'APPROVED')
  );

-- Warehouse-level allocation lines.
INSERT INTO allocation_items (allocation_id, warehouse_id, quantity, distance_km, reason)
SELECT al.id, w.id, v.quantity, v.distance_km, v.reason
FROM (VALUES
    ('DEMO: Food stocks exhausted; road access is cut.', 'PTL-01', 900, 2.4,
     'Patiala depot food batch; first allocation source.'),
    ('DEMO: Food stocks exhausted; road access is cut.', 'RPR-01', 600, 24.8,
     'Rajpura depot food batch; completes the food request.'),
    ('DEMO: Verified water requirement for the Khera flood response.', 'PTL-01', 2500, 3.1,
     'Patiala depot water stock covers the verified request.'),
    ('DEMO: Verified medical requirement for the Kalan flood response.', 'RPR-01', 25, 24.8,
     'Partial first-aid recommendation; 15 kits remain unmet.')
) AS v(request_description, warehouse_code, quantity, distance_km, reason)
JOIN need_requests n ON n.description = v.request_description
JOIN allocations al ON al.request_id = n.id AND al.status IN ('APPROVED', 'RECOMMENDED')
JOIN warehouses w ON w.code = v.warehouse_code
WHERE NOT EXISTS (
    SELECT 1 FROM allocation_items ai
    WHERE ai.allocation_id = al.id AND ai.warehouse_id = w.id
);

-- Explain why Samana was not selected for the medical recommendation.
INSERT INTO allocation_not_selected (allocation_id, warehouse_id, reason)
SELECT al.id, w.id, 'DEMO: No suitable first-aid batch is allocated from this depot in this recommendation.'
FROM need_requests n
JOIN allocations al ON al.request_id = n.id AND al.status = 'RECOMMENDED'
JOIN warehouses w ON w.code = 'SMN-01'
WHERE n.description = 'DEMO: Verified medical requirement for the Kalan flood response.'
  AND NOT EXISTS (
      SELECT 1 FROM allocation_not_selected x
      WHERE x.allocation_id = al.id AND x.warehouse_id = w.id
  );

-- Reserve the stock for approved allocations exactly once. The CTE only changes
-- inventory and writes a RESERVATION ledger row for newly inserted reservations.
WITH reservation_seed AS (
    SELECT al.id AS allocation_id, i.id AS inventory_id, v.quantity,
           n.id AS request_id, u.id AS actor_user_id,
           'DEMO-RES-' || v.reference AS external_reference
    FROM (VALUES
        ('DEMO: Food stocks exhausted; road access is cut.', 'PTL-01', 'Packaged food ration', 900, 'FOOD-PTL'),
        ('DEMO: Food stocks exhausted; road access is cut.', 'RPR-01', 'Packaged food ration', 600, 'FOOD-RPR'),
        ('DEMO: Verified water requirement for the Khera flood response.', 'PTL-01', 'Drinking water', 2500, 'WATER-PTL')
    ) AS v(request_description, warehouse_code, resource_name, quantity, reference)
    JOIN need_requests n ON n.description = v.request_description
    JOIN allocations al ON al.request_id = n.id AND al.status = 'APPROVED'
    JOIN warehouses w ON w.code = v.warehouse_code
    JOIN resources r ON lower(r.name) = lower(v.resource_name)
    JOIN inventory i ON i.warehouse_id = w.id AND i.resource_id = r.id
    CROSS JOIN users u
    WHERE lower(u.email) = 'officer@demo.drrc.local'
      AND i.quantity_on_hand - i.quantity_reserved >= v.quantity
), inserted_reservations AS (
    INSERT INTO allocation_reservations (allocation_id, inventory_id, quantity, dispatched)
    SELECT allocation_id, inventory_id, quantity, FALSE
    FROM reservation_seed
    ON CONFLICT (allocation_id, inventory_id) DO NOTHING
    RETURNING allocation_id, inventory_id, quantity
), updated_inventory AS (
    UPDATE inventory i
    SET quantity_reserved = i.quantity_reserved + ir.quantity,
        updated_at = now()
    FROM inserted_reservations ir
    WHERE i.id = ir.inventory_id
    RETURNING i.id, ir.quantity, ir.allocation_id
)
INSERT INTO inventory_transactions (
    inventory_id, transaction_type, quantity_delta_on_hand, quantity_delta_reserved,
    request_id, actor_user_id, reason, external_reference, is_demo
)
SELECT ui.id, 'RESERVATION', 0, ui.quantity, rs.request_id, rs.actor_user_id,
       'DEMO: Stock reserved for an approved seeded allocation.',
       'DEMO-RESERVATION-' || ui.allocation_id || '-' || ui.id, TRUE
FROM updated_inventory ui
JOIN reservation_seed rs
  ON rs.allocation_id = ui.allocation_id
 AND rs.inventory_id = ui.id
 AND rs.quantity = ui.quantity;

-- Allocation lifecycle history.
INSERT INTO request_history (request_id, action, from_status, to_status, actor_user_id, reason)
SELECT n.id, 'ALLOCATED', 'APPROVED', 'ALLOCATED', officer.id, 'DEMO: Allocation and stock reservation created.'
FROM need_requests n
JOIN allocations al ON al.request_id = n.id AND al.status = 'APPROVED'
CROSS JOIN users officer
WHERE lower(officer.email) = 'officer@demo.drrc.local'
  AND NOT EXISTS (
      SELECT 1 FROM request_history h
      WHERE h.request_id = n.id AND h.action = 'ALLOCATED'
  );
