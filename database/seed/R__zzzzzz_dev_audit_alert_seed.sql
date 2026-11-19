-- Development/demo audit and alert records.
-- Runs after the operational, dispatch, volunteer and partner demo seeds.
-- Audit rows are append-only; this script only inserts and is idempotent.
-- Never load in production.

-- A compact audit trail for the seeded workflow. These rows are illustrative
-- and are not a substitute for audit events written by the application.
INSERT INTO audit_logs (
    actor_user_id, actor_role, action, entity_type, entity_id, details, created_at
)
SELECT actor.id, v.actor_role, v.action, v.entity_type, v.entity_id, v.details, v.created_at
FROM (VALUES
    ('officer@demo.drrc.local', 'OFFICER', 'DEMO_DISASTER_REVIEWED', 'DISASTER', NULL::BIGINT,
     'DEMO: Simulated Ghaggar River Flood reviewed for the demonstration.',
     TIMESTAMPTZ '2026-11-18 09:00:00+05:30'),
    ('officer@demo.drrc.local', 'OFFICER', 'DEMO_REQUEST_VERIFIED', 'NEED_REQUEST', NULL::BIGINT,
     'DEMO: Verified water and medical requests for the Khera/Kalan walkthrough.',
     TIMESTAMPTZ '2026-11-18 09:30:00+05:30'),
    ('officer@demo.drrc.local', 'OFFICER', 'DEMO_ALLOCATION_APPROVED', 'ALLOCATION', NULL::BIGINT,
     'DEMO: Food and water allocations approved; medical recommendation remains unapproved.',
     TIMESTAMPTZ '2026-11-18 10:10:00+05:30'),
    ('warehouse1@demo.drrc.local', 'WAREHOUSE_MANAGER', 'DEMO_DISPATCH_CREATED', 'DISPATCH', NULL::BIGINT,
     'DEMO: Food deliveries completed and water shipment placed in transit.',
     TIMESTAMPTZ '2026-11-18 10:30:00+05:30'),
    ('ngo@demo.drrc.local', 'NGO', 'DEMO_PARTNER_OFFER_RECORDED', 'PARTNER_CONTRIBUTION', NULL::BIGINT,
     'DEMO: NGO food, medicine, water and first-aid contributions recorded.',
     TIMESTAMPTZ '2026-11-18 10:40:00+05:30')
) AS v(actor_email, actor_role, action, entity_type, entity_id, details, created_at)
JOIN users actor ON lower(actor.email) = v.actor_email
  AND NOT EXISTS (
      SELECT 1 FROM audit_logs a
      WHERE a.action = v.action AND a.details = v.details
  );

-- Alerts are seeded from actual demo entities where possible.
INSERT INTO alerts (type, severity, message, entity_type, entity_id, status, created_at)
SELECT 'CRITICAL_REQUEST', 'CRITICAL',
       'DEMO: Critical drinking-water request requires priority attention.',
       'NEED_REQUEST', n.id, 'NEW', TIMESTAMPTZ '2026-11-18 09:35:00+05:30'
FROM need_requests n
WHERE n.description = 'DEMO: Verified water requirement for the Khera flood response.'
  AND NOT EXISTS (
      SELECT 1 FROM alerts a
      WHERE a.type = 'CRITICAL_REQUEST' AND a.entity_type = 'NEED_REQUEST' AND a.entity_id = n.id
  );

INSERT INTO alerts (type, severity, message, entity_type, entity_id, status, created_at)
SELECT 'LOW_STOCK', 'HIGH',
       'DEMO: First-aid inventory is limited; the current recommendation leaves a 15-kit shortage.',
       'NEED_REQUEST', n.id, 'NEW', TIMESTAMPTZ '2026-11-18 10:15:00+05:30'
FROM need_requests n
WHERE n.description = 'DEMO: Verified medical requirement for the Kalan flood response.'
  AND NOT EXISTS (
      SELECT 1 FROM alerts a
      WHERE a.type = 'LOW_STOCK' AND a.entity_type = 'NEED_REQUEST' AND a.entity_id = n.id
  );

INSERT INTO alerts (type, severity, message, entity_type, entity_id, status, created_at)
SELECT 'OVERCROWDED_SHELTER', 'CRITICAL',
       'DEMO: Samana Relief Camp occupancy exceeds its planned capacity.',
       'SHELTER', s.id, 'NEW', TIMESTAMPTZ '2026-11-18 10:20:00+05:30'
FROM shelters s
WHERE s.code = 'SH-SMN-01'
  AND s.current_occupancy > s.capacity_people
  AND NOT EXISTS (
      SELECT 1 FROM alerts a
      WHERE a.type = 'OVERCROWDED_SHELTER' AND a.entity_type = 'SHELTER' AND a.entity_id = s.id
  );

INSERT INTO alerts (type, severity, message, entity_type, entity_id, status, created_at)
SELECT 'DISPATCHED', 'MEDIUM',
       'DEMO: Water shipment has departed and is currently in transit to Khera Village.',
       'DISPATCH', d.id, 'NEW', TIMESTAMPTZ '2026-11-18 10:35:00+05:30'
FROM dispatches d
JOIN need_requests n ON n.id = d.request_id
WHERE n.description = 'DEMO: Verified water requirement for the Khera flood response.'
  AND d.status = 'IN_TRANSIT'
  AND NOT EXISTS (
      SELECT 1 FROM alerts a
      WHERE a.type = 'DISPATCHED' AND a.entity_type = 'DISPATCH' AND a.entity_id = d.id
  );

INSERT INTO alerts (type, severity, message, entity_type, entity_id, status, created_at)
SELECT 'DELIVERED', 'LOW',
       'DEMO: Food-ration shipment delivery has been confirmed.',
       'DISPATCH', d.id, 'NEW', TIMESTAMPTZ '2026-11-18 12:35:00+05:30'
FROM dispatches d
JOIN need_requests n ON n.id = d.request_id
CROSS JOIN users officer
WHERE lower(officer.email) = 'officer@demo.drrc.local'
  AND n.description = 'DEMO: Food stocks exhausted; road access is cut.'
  AND d.status = 'DELIVERED'
  AND NOT EXISTS (
      SELECT 1 FROM alerts a
      WHERE a.type = 'DELIVERED' AND a.entity_type = 'DISPATCH' AND a.entity_id = d.id
  );

-- Mark the seeded delivered alerts as read by the officer.
UPDATE alerts a
SET status = 'READ', read_by = officer.id,
    read_at = COALESCE(a.read_at, TIMESTAMPTZ '2026-11-18 12:40:00+05:30')
FROM users officer
WHERE lower(officer.email) = 'officer@demo.drrc.local'
  AND a.type = 'DELIVERED'
  AND a.message = 'DEMO: Food-ration shipment delivery has been confirmed.'
  AND a.status IN ('NEW', 'READ')
  AND a.read_by IS NULL;
