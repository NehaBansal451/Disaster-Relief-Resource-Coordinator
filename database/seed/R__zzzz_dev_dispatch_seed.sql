-- Development/demo dispatch and delivery timeline.
-- Runs after R__zzz_dev_allocation_seed.sql. Repeatable and idempotent.
-- Dispatch rows consume only stock reserved by the allocation seed.
-- Never load in production.

WITH dispatch_seed AS (
    SELECT al.id AS allocation_id, n.id AS request_id, w.id AS warehouse_id,
           v.status, v.quantity, v.vehicle, v.driver_name, v.note, officer.id AS created_by,
           TIMESTAMPTZ '2026-11-18 10:30:00+05:30' AS dispatched_at,
           CASE WHEN v.status = 'DELIVERED'
                THEN TIMESTAMPTZ '2026-11-18 12:30:00+05:30' ELSE NULL END AS delivered_at
    FROM (VALUES
        ('DEMO: Food stocks exhausted; road access is cut.', 'PTL-01', 'DELIVERED', 900,
         'DEMO Relief Truck 01', 'Demo Driver A', 'DEMO: Food delivered to the Majra distribution point.'),
        ('DEMO: Food stocks exhausted; road access is cut.', 'RPR-01', 'DELIVERED', 600,
         'DEMO Relief Van 02', 'Demo Driver B', 'DEMO: Remaining food delivered to the Majra distribution point.'),
        ('DEMO: Verified water requirement for the Khera flood response.', 'PTL-01', 'IN_TRANSIT', 2500,
         'DEMO Water Tanker 03', 'Demo Driver C', 'DEMO: Water shipment is on the way to Khera Village.')
    ) AS v(request_description, warehouse_code, status, quantity, vehicle, driver_name, note)
    JOIN need_requests n ON n.description = v.request_description
    JOIN allocations al ON al.request_id = n.id AND al.status = 'APPROVED'
    JOIN warehouses w ON w.code = v.warehouse_code
    CROSS JOIN users officer
    WHERE lower(officer.email) = 'officer@demo.drrc.local'
      AND EXISTS (
          SELECT 1
          FROM allocation_reservations ar
          JOIN inventory i ON i.id = ar.inventory_id
          WHERE ar.allocation_id = al.id
            AND i.warehouse_id = w.id
            AND ar.quantity = v.quantity
            AND ar.dispatched = FALSE
      )
), inserted_dispatches AS (
    INSERT INTO dispatches (
        allocation_id, request_id, warehouse_id, status, quantity, vehicle,
        driver_name, note, created_by, dispatched_at, delivered_at
    )
    SELECT allocation_id, request_id, warehouse_id, status, quantity, vehicle,
           driver_name, note, created_by, dispatched_at, delivered_at
    FROM dispatch_seed
    ON CONFLICT (allocation_id, warehouse_id) DO NOTHING
    RETURNING id, allocation_id, request_id, warehouse_id, status, quantity, created_by
), marked_reservations AS (
    UPDATE allocation_reservations ar
    SET dispatched = TRUE
    FROM inserted_dispatches d
    JOIN inventory i ON i.warehouse_id = d.warehouse_id
    WHERE ar.allocation_id = d.allocation_id
      AND ar.inventory_id = i.id
      AND ar.quantity = d.quantity
      AND ar.dispatched = FALSE
    RETURNING ar.inventory_id, ar.quantity, d.request_id, d.created_by, d.id AS dispatch_id
), updated_inventory AS (
    UPDATE inventory i
    SET quantity_on_hand = i.quantity_on_hand - mr.quantity,
        quantity_reserved = i.quantity_reserved - mr.quantity,
        updated_at = now()
    FROM marked_reservations mr
    WHERE i.id = mr.inventory_id
      AND i.quantity_on_hand >= mr.quantity
      AND i.quantity_reserved >= mr.quantity
    RETURNING i.id, mr.quantity, mr.request_id, mr.created_by, mr.dispatch_id
)
INSERT INTO inventory_transactions (
    inventory_id, transaction_type, quantity_delta_on_hand, quantity_delta_reserved,
    request_id, actor_user_id, reason, external_reference, is_demo
)
SELECT ui.id, 'DISPATCH', -ui.quantity, -ui.quantity, ui.request_id, ui.created_by,
       'DEMO: Reserved stock dispatched to the affected area.',
       'DEMO-DISPATCH-' || ui.dispatch_id, TRUE
FROM updated_inventory ui
WHERE NOT EXISTS (
    SELECT 1 FROM inventory_transactions tx
    WHERE tx.external_reference = 'DEMO-DISPATCH-' || ui.dispatch_id
);

-- Add the initial DISPATCHED event for every seeded dispatch.
INSERT INTO delivery_updates (dispatch_id, status, note, actor_user_id, created_at)
SELECT d.id, 'DISPATCHED', 'DEMO: Shipment departed from the warehouse.', d.created_by, d.dispatched_at
FROM dispatches d
JOIN allocations al ON al.id = d.allocation_id
JOIN need_requests n ON n.id = al.request_id
WHERE n.is_demo
  AND d.note LIKE 'DEMO:%'
  AND NOT EXISTS (
      SELECT 1 FROM delivery_updates du
      WHERE du.dispatch_id = d.id AND du.status = 'DISPATCHED'
  );

-- Add the next event according to the current seeded dispatch state.
INSERT INTO delivery_updates (dispatch_id, status, note, actor_user_id, created_at)
SELECT d.id, d.status,
       CASE d.status
           WHEN 'IN_TRANSIT' THEN 'DEMO: Vehicle has departed and is approaching the affected village.'
           WHEN 'DELIVERED' THEN 'DEMO: Recipient confirmed receipt of the shipment.'
       END,
       d.created_by,
       CASE WHEN d.status = 'DELIVERED'
            THEN COALESCE(d.delivered_at, d.dispatched_at + INTERVAL '2 hours')
            ELSE d.dispatched_at + INTERVAL '30 minutes'
       END
FROM dispatches d
JOIN allocations al ON al.id = d.allocation_id
JOIN need_requests n ON n.id = al.request_id
WHERE n.is_demo
  AND d.status IN ('IN_TRANSIT', 'DELIVERED')
  AND NOT EXISTS (
      SELECT 1 FROM delivery_updates du
      WHERE du.dispatch_id = d.id AND du.status = d.status
  );

-- Reflect the delivery state on the parent requests.
UPDATE need_requests n
SET status = CASE
        WHEN n.description = 'DEMO: Food stocks exhausted; road access is cut.' THEN 'DELIVERED'
        WHEN n.description = 'DEMO: Verified water requirement for the Khera flood response.' THEN 'IN_TRANSIT'
        ELSE n.status
    END,
    updated_at = now()
WHERE n.is_demo
  AND n.description IN (
      'DEMO: Food stocks exhausted; road access is cut.',
      'DEMO: Verified water requirement for the Khera flood response.'
  )
  AND n.status IN ('APPROVED', 'ALLOCATED', 'DISPATCHED', 'IN_TRANSIT');

-- Request history for dispatch and delivery milestones.
INSERT INTO request_history (request_id, action, from_status, to_status, actor_user_id, reason)
SELECT n.id, 'DISPATCHED', 'ALLOCATED', 'DISPATCHED', officer.id, 'DEMO: Seeded dispatch created.'
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
      WHERE h.request_id = n.id AND h.action = 'DISPATCHED'
  );

INSERT INTO request_history (request_id, action, from_status, to_status, actor_user_id, reason)
SELECT n.id, 'IN_TRANSIT', 'DISPATCHED', 'IN_TRANSIT', officer.id, 'DEMO: Water shipment is in transit.'
FROM need_requests n
CROSS JOIN users officer
WHERE lower(officer.email) = 'officer@demo.drrc.local'
  AND n.is_demo
  AND n.description = 'DEMO: Verified water requirement for the Khera flood response.'
  AND NOT EXISTS (
      SELECT 1 FROM request_history h
      WHERE h.request_id = n.id AND h.action = 'IN_TRANSIT'
  );

INSERT INTO request_history (request_id, action, from_status, to_status, actor_user_id, reason)
SELECT n.id, 'DELIVERED', 'IN_TRANSIT', 'DELIVERED', officer.id, 'DEMO: Food shipment delivery confirmed.'
FROM need_requests n
CROSS JOIN users officer
WHERE lower(officer.email) = 'officer@demo.drrc.local'
  AND n.is_demo
  AND n.description = 'DEMO: Food stocks exhausted; road access is cut.'
  AND NOT EXISTS (
      SELECT 1 FROM request_history h
      WHERE h.request_id = n.id AND h.action = 'DELIVERED'
  );
