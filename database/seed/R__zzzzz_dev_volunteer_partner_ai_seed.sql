-- Development/demo volunteer, partner contribution and AI analysis data.
-- Runs after the base, allocation and dispatch seeds. Repeatable and idempotent.
-- All names, contact details and reports are fictional. Never load in production.

-- Volunteer rows. One record is linked to the seeded demo volunteer account;
-- the remaining records are standalone volunteer profiles.
INSERT INTO volunteers (
    user_id, organization_id, full_name, phone, skills, availability,
    district, state, location, is_demo, created_by
)
SELECT
    CASE WHEN v.email IS NULL THEN NULL ELSE u.id END,
    org.id,
    v.full_name,
    v.phone,
    v.skills,
    v.availability,
    'Patiala',
    'Punjab',
    ST_SetSRID(ST_MakePoint(v.longitude, v.latitude), 4326)::geography,
    TRUE,
    officer.id
FROM (VALUES
    ('Demo Volunteer', 'volunteer@demo.drrc.local', '+91-00000-03001',
     'First aid, registration, crowd coordination', 'BUSY', 76.3860, 30.3410),
    ('Demo Volunteer Asha', NULL, '+91-00000-03002',
     'Medical assistance, first aid', 'AVAILABLE', 76.1500, 30.2850),
    ('Demo Volunteer Kabir', NULL, '+91-00000-03003',
     'Driving, logistics, warehouse operations', 'BUSY', 76.1050, 30.2400),
    ('Demo Volunteer Meera', NULL, '+91-00000-03004',
     'Food distribution, community outreach', 'AVAILABLE', 76.0700, 30.3300),
    ('Demo Volunteer Arjun', NULL, '+91-00000-03005',
     'Water distribution, basic rescue support', 'OFF_DUTY', 76.1900, 30.1500),
    ('Demo Volunteer Noor', NULL, '+91-00000-03006',
     'Translation, registration, family support', 'AVAILABLE', 76.5940, 30.4840)
) AS v(full_name, email, phone, skills, availability, longitude, latitude)
LEFT JOIN users u ON lower(u.email) = lower(v.email)
LEFT JOIN organizations org ON org.name = 'DEMO Rural Health Volunteers'
CROSS JOIN users officer
WHERE lower(officer.email) = 'officer@demo.drrc.local'
  AND NOT EXISTS (
      SELECT 1 FROM volunteers x
      WHERE x.is_demo AND x.full_name = v.full_name
  );

-- Volunteer assignments against the simulated flood and its requests.
INSERT INTO volunteer_assignments (
    volunteer_id, disaster_id, request_id, task, status, assigned_by
)
SELECT v.id, d.id, n.id, a.task, a.status, officer.id
FROM (VALUES
    ('Demo Volunteer', 'DEMO: Verified medical requirement for the Kalan flood response.',
     'Coordinate first-aid kit distribution at Kalan Village.', 'ACCEPTED'),
    ('Demo Volunteer Asha', 'DEMO: Verified medical requirement for the Kalan flood response.',
     'Support the village clinic and maintain a first-aid distribution register.', 'ASSIGNED'),
    ('Demo Volunteer Kabir', 'DEMO: Verified water requirement for the Khera flood response.',
     'Coordinate unloading and safe distribution of the incoming water shipment.', 'ACCEPTED'),
    ('Demo Volunteer Meera', 'DEMO: Food stocks exhausted; road access is cut.',
     'Coordinate household food-ration handover and recipient counts.', 'COMPLETED'),
    ('Demo Volunteer Noor', 'DEMO: Food stocks exhausted; road access is cut.',
     'Help residents register and provide Punjabi/Hindi translation.', 'ASSIGNED')
) AS a(volunteer_name, request_description, task, status)
JOIN volunteers v ON v.full_name = a.volunteer_name AND v.is_demo
JOIN disasters d ON d.name = 'DEMO: Simulated Ghaggar River Flood'
JOIN need_requests n ON n.description = a.request_description
CROSS JOIN users officer
WHERE lower(officer.email) = 'officer@demo.drrc.local'
  AND NOT EXISTS (
      SELECT 1 FROM volunteer_assignments va
      WHERE va.volunteer_id = v.id
        AND va.request_id = n.id
        AND va.task = a.task
  );

-- NGO/partner offers. RECEIVED means the offer was received by the organization;
-- it deliberately does not add warehouse inventory automatically.
INSERT INTO partner_contributions (
    organization_id, resource_id, quantity, description, status,
    decided_by, created_by
)
SELECT org.id, r.id, v.quantity, v.description, v.status,
       CASE WHEN v.status IN ('ACCEPTED', 'DECLINED', 'RECEIVED') THEN officer.id ELSE NULL END,
       ngo_user.id
FROM (VALUES
    ('Packaged food ration', 1000,
     'DEMO: Offer of 1,000 food-ration packets for the flood response.', 'ACCEPTED'),
    ('Essential medicines pack', 120,
     'DEMO: Offer of 120 essential-medicine packs awaiting review.', 'OFFERED'),
    ('Drinking water', 2000,
     'DEMO: 2,000 litres of bottled drinking water received for onward warehouse intake.', 'RECEIVED'),
    ('First-aid kit', 50,
     'DEMO: Offer of 50 first-aid kits declined because the batch documentation was incomplete.', 'DECLINED')
) AS v(resource_name, quantity, description, status)
JOIN resources r ON lower(r.name) = lower(v.resource_name)
JOIN organizations org ON org.name = 'DEMO Jal Seva Relief Trust'
CROSS JOIN users ngo_user
CROSS JOIN users officer
WHERE lower(ngo_user.email) = 'ngo@demo.drrc.local'
  AND lower(officer.email) = 'officer@demo.drrc.local'
  AND NOT EXISTS (
      SELECT 1 FROM partner_contributions pc
      WHERE pc.description = v.description
  );

-- Synthetic AI analysis records. No real personal information is included.
-- The result is explicitly stored as an unverified suggestion; it does not
-- create or modify any operational request.
INSERT INTO ai_analyses (
    report_text, model, status, result_json, error_code,
    review_status, reviewed_by, reviewed_at, created_by, created_at
)
SELECT v.report_text, 'demo-synthetic-model', v.status, v.result_json, v.error_code,
       v.review_status,
       CASE WHEN v.review_status = 'UNREVIEWED' THEN NULL ELSE officer.id END,
       CASE WHEN v.review_status = 'UNREVIEWED' THEN NULL
            ELSE TIMESTAMPTZ '2026-11-18 11:00:00+05:30' END,
       officer.id,
       TIMESTAMPTZ '2026-11-18 10:45:00+05:30'
FROM (VALUES
    (
      'DEMO REPORT: Floodwater has affected low-lying lanes in Khera Village. Drinking water access is disrupted. The report recommends prioritizing safe water distribution and checking vulnerable households.',
      'SUCCEEDED',
      '{"summary":"Flood impact and water access disruption reported in Khera Village.","suggested_severity":"CRITICAL","recommended_actions":["Prioritize safe drinking-water distribution","Verify household counts","Coordinate accessible delivery points"],"confidence":"DEMO_ONLY"}',
      NULL,
      'ACCEPTED'
    ),
    (
      'DEMO REPORT: Road access to Majra Village is interrupted and household food stocks are reported exhausted. The report recommends coordinated food-ration distribution and route verification.',
      'SUCCEEDED',
      '{"summary":"Food shortage and access disruption reported in Majra Village.","suggested_severity":"HIGH","recommended_actions":["Verify safe access route","Coordinate food-ration distribution"],"confidence":"DEMO_ONLY"}',
      NULL,
      'UNREVIEWED'
    ),
    (
      'DEMO REPORT: A synthetic analyzer failure record for demonstrating unavailable model handling. No real incident report was submitted.',
      'FAILED',
      NULL,
      'DEMO_PROVIDER_UNAVAILABLE',
      'UNREVIEWED'
    )
) AS v(report_text, status, result_json, error_code, review_status)
CROSS JOIN users officer
WHERE lower(officer.email) = 'officer@demo.drrc.local'
  AND NOT EXISTS (
      SELECT 1 FROM ai_analyses a
      WHERE a.report_text = v.report_text
  );
