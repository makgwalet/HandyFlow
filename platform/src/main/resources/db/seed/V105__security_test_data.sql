-- =============================================================================
-- V105__security_test_data.sql
-- Security Module — QA Seed Data
--
-- Tenant A: f3ca02b3-eca5-4035-8756-941c72ab6512  (Acme Security Solutions)
-- Tenant B: aeb2b97f-9523-4bd7-a060-685a10c24831  (Beta Construction)
--
-- UUID namespace (all valid hex, prefix-based to avoid collision with gen_random_uuid):
--   a1000000-... = guards       a2000000-... = sites
--   a3000000-... = checkpoints  a4000000-... = shifts
--   a5000000-... = incidents    a6000000-... = audit_log
--
-- Run: DELETE FROM flyway_schema_history WHERE version='105' AND success=false;
--      then: Get-Content V105__security_test_data.sql | docker exec -i handyflow-db psql -U handyflow -d handyflow
-- Or as Flyway migration: copy to db/migration/ then mvn clean spring-boot:run
-- =============================================================================

BEGIN;

DO $$
DECLARE
    tenant_a UUID := 'f3ca02b3-eca5-4035-8756-941c72ab6512';
    tenant_b UUID := 'aeb2b97f-9523-4bd7-a060-685a10c24831';

    -- Guards  (a1000000-...)
    g01 UUID := 'a1000000-0000-0000-0000-000000000001';  -- James Dlamini      C  ACTIVE
    g02 UUID := 'a1000000-0000-0000-0000-000000000002';  -- Sipho Nkosi        D  ACTIVE
    g03 UUID := 'a1000000-0000-0000-0000-000000000003';  -- Thandi Mokoena     B  ACTIVE
    g04 UUID := 'a1000000-0000-0000-0000-000000000004';  -- Kabelo Sithole     C  ON_LEAVE
    g05 UUID := 'a1000000-0000-0000-0000-000000000005';  -- Lerato Vilakazi    D  SUSPENDED
    g06 UUID := 'a1000000-0000-0000-0000-000000000006';  -- Bongani Khumalo    C  ACTIVE (PSiRA 22d)
    g07 UUID := 'a1000000-0000-0000-0000-000000000007';  -- Ayanda Ndaba       E  ACTIVE
    g08 UUID := 'a1000000-0000-0000-0000-000000000008';  -- Marco van der Berg A  ACTIVE
    gb1 UUID := 'a1000000-0000-0000-0000-000000000091';  -- Tenant B guard

    -- Sites  (a2000000-...)
    s01 UUID := 'a2000000-0000-0000-0000-000000000001';  -- Sandton City Mall
    s02 UUID := 'a2000000-0000-0000-0000-000000000002';  -- Rosebank Office Park
    s03 UUID := 'a2000000-0000-0000-0000-000000000003';  -- Midrand Warehouse
    s04 UUID := 'a2000000-0000-0000-0000-000000000004';  -- Randburg Distribution Centre (TERMINATED)
    sb1 UUID := 'a2000000-0000-0000-0000-000000000091';  -- Tenant B site

    -- Checkpoints  (a3000000-...)
    c01 UUID := 'a3000000-0000-0000-0000-000000000001';
    c02 UUID := 'a3000000-0000-0000-0000-000000000002';
    c03 UUID := 'a3000000-0000-0000-0000-000000000003';
    c04 UUID := 'a3000000-0000-0000-0000-000000000004';
    c05 UUID := 'a3000000-0000-0000-0000-000000000005';
    c06 UUID := 'a3000000-0000-0000-0000-000000000006';
    c07 UUID := 'a3000000-0000-0000-0000-000000000007';
    c08 UUID := 'a3000000-0000-0000-0000-000000000008';
    c09 UUID := 'a3000000-0000-0000-0000-000000000009';
    c0a UUID := 'a3000000-0000-0000-0000-00000000000a';

    -- Shifts  (a4000000-...)
    sh01 UUID := 'a4000000-0000-0000-0000-000000000001';  -- ACTIVE   James   Sandton   today
    sh02 UUID := 'a4000000-0000-0000-0000-000000000002';  -- ACTIVE   Sipho   Rosebank  today
    sh03 UUID := 'a4000000-0000-0000-0000-000000000003';  -- ACTIVE   Marco   Midrand   today
    sh04 UUID := 'a4000000-0000-0000-0000-000000000004';  -- SCHEDULED Thandi Sandton   tonight
    sh05 UUID := 'a4000000-0000-0000-0000-000000000005';  -- SCHEDULED Bongani Rosebank tomorrow
    sh06 UUID := 'a4000000-0000-0000-0000-000000000006';  -- SCHEDULED Ayanda  Midrand  tomorrow
    sh07 UUID := 'a4000000-0000-0000-0000-000000000007';  -- COMPLETED James   Sandton  yesterday
    sh08 UUID := 'a4000000-0000-0000-0000-000000000008';  -- COMPLETED Sipho   Rosebank yesterday
    sh09 UUID := 'a4000000-0000-0000-0000-000000000009';  -- COMPLETED Thandi  Sandton  2 days ago
    sh0a UUID := 'a4000000-0000-0000-0000-00000000000a';  -- MISSED   Kabelo  Midrand  2 days ago
    sh0b UUID := 'a4000000-0000-0000-0000-00000000000b';  -- CANCELLED Lerato Rosebank  last week

    -- Incidents  (a5000000-...)
    i01 UUID := 'a5000000-0000-0000-0000-000000000001';
    i02 UUID := 'a5000000-0000-0000-0000-000000000002';
    i03 UUID := 'a5000000-0000-0000-0000-000000000003';
    i04 UUID := 'a5000000-0000-0000-0000-000000000004';
    i05 UUID := 'a5000000-0000-0000-0000-000000000005';
    i06 UUID := 'a5000000-0000-0000-0000-000000000006';

    -- Audit log  (a6000000-...)
    al01 UUID := 'a6000000-0000-0000-0000-000000000001';
    al02 UUID := 'a6000000-0000-0000-0000-000000000002';
    al03 UUID := 'a6000000-0000-0000-0000-000000000003';
    al04 UUID := 'a6000000-0000-0000-0000-000000000004';

    -- Time anchors (always relative to NOW)
    today_6am  TIMESTAMPTZ := DATE_TRUNC('day', NOW()) + INTERVAL '6 hours';
    today_22pm TIMESTAMPTZ := DATE_TRUNC('day', NOW()) + INTERVAL '22 hours';
    tmrw_6am   TIMESTAMPTZ := DATE_TRUNC('day', NOW()) + INTERVAL '1 day 6 hours';
    yest_6am   TIMESTAMPTZ := DATE_TRUNC('day', NOW()) - INTERVAL '18 hours';
    two_days_6 TIMESTAMPTZ := DATE_TRUNC('day', NOW()) - INTERVAL '42 hours';
    week_ago   TIMESTAMPTZ := NOW() - INTERVAL '7 days';

BEGIN

-- ==========================================================================
-- 1. GUARDS
-- PSiRA range PSR-2024-8xxxx avoids existing PSR-2022-089
-- SA IDs all different from existing 9501200098073
-- ==========================================================================

INSERT INTO security_guards (
    id, tenant_id, first_name, last_name, psira_number, id_number,
    phone, grade, active, status, status_note, status_changed_at,
    psira_expiry_date, notes, created_at, updated_at
) VALUES
(g01, tenant_a, 'James',   'Dlamini',
 'PSR-2024-80001', '8501015026082', '+27 82 111 0001', 'C',
 true, 'ACTIVE', NULL, NULL,
 CURRENT_DATE + INTERVAL '18 months', 'Experienced mall guard.',
 NOW()-INTERVAL'365d', NOW()),

(g02, tenant_a, 'Sipho',   'Nkosi',
 'PSR-2024-80002', '9003285026087', '+27 71 222 0002', 'D',
 true, 'ACTIVE', NULL, NULL,
 CURRENT_DATE + INTERVAL '8 months', NULL,
 NOW()-INTERVAL'300d', NOW()),

(g03, tenant_a, 'Thandi',  'Mokoena',
 'PSR-2024-80003', '9205100026080', '+27 83 333 0003', 'B',
 true, 'ACTIVE', NULL, NULL,
 CURRENT_DATE + INTERVAL '2 years', 'Grade B supervisor cert June 2024.',
 NOW()-INTERVAL'250d', NOW()),

(g04, tenant_a, 'Kabelo',  'Sithole',
 'PSR-2024-80004', '8803155026083', '+27 72 444 0004', 'C',
 true, 'ON_LEAVE', 'Annual leave approved 1-15 July 2026', NOW()-INTERVAL'5d',
 CURRENT_DATE + INTERVAL '10 months', NULL,
 NOW()-INTERVAL'200d', NOW()),

(g05, tenant_a, 'Lerato',  'Vilakazi',
 'PSR-2024-80005', '9107205026086', '+27 82 555 0005', 'D',
 true, 'SUSPENDED',
 'Pending disciplinary hearing HR-2026-041. Late 5 consecutive days.',
 NOW()-INTERVAL'12d',
 CURRENT_DATE + INTERVAL '5 months', NULL,
 NOW()-INTERVAL'180d', NOW()),

(g06, tenant_a, 'Bongani', 'Khumalo',
 'PSR-2024-80006', '8706105026089', '+27 71 666 0006', 'C',
 true, 'ACTIVE', NULL, NULL,
 CURRENT_DATE + INTERVAL '22 days',
 'PSiRA renewal submitted, awaiting certificate.',
 NOW()-INTERVAL'400d', NOW()),

(g07, tenant_a, 'Ayanda',  'Ndaba',
 'PSR-2024-80007', '0001015026085', '+27 83 777 0007', 'E',
 true, 'ACTIVE', NULL, NULL,
 CURRENT_DATE + INTERVAL '3 years', 'Junior guard, first deployment.',
 NOW()-INTERVAL'60d', NOW()),

(g08, tenant_a, 'Marco',   'van der Berg',
 'PSR-2024-80008', '7502135026080', '+27 72 888 0008', 'A',
 true, 'ACTIVE', NULL, NULL,
 CURRENT_DATE + INTERVAL '14 months', 'Site commander. Armed response certified.',
 NOW()-INTERVAL'500d', NOW()),

(gb1, tenant_b, 'Beta', 'Guard',
 'PSR-2024-89991', NULL, '+27 11 900 0001', 'D',
 true, 'ACTIVE', NULL, NULL, NULL,
 'Tenant B isolation guard.',
 NOW(), NOW());


-- ==========================================================================
-- 2. SITES
-- "OR Tambo Cargo Terminal" renamed to "Randburg Distribution Centre"
-- to avoid clash with the existing site of that name in the DB
-- ==========================================================================

INSERT INTO security_sites (
    id, tenant_id, customer_id, name, address, latitude, longitude,
    contact_name, contact_phone, instructions,
    qr_secret, active, contract_status, contract_start, contract_end,
    termination_reason, terminated_at,
    portal_token, portal_enabled, portal_label,
    created_at, updated_at
) VALUES
(s01, tenant_a, NULL, 'Sandton City Mall',
 '{"street":"163 Rivonia Rd","suburb":"Sandton","city":"Johannesburg","province":"GP","postalCode":"2196"}'::jsonb,
 -26.10760, 28.05640, 'Johan Pretorius', '+27 11 883 8600',
 'Report to Security Control Room Level 2 East Wing. Access code: 7734.',
 'a2000001secret32charsseed000001',
 true, 'ACTIVE', '2025-01-01', '2026-12-31', NULL, NULL,
 'portal-sandton-seed-demo-tok-0001', true,
 'Sandton City Mall - Security Dashboard',
 NOW()-INTERVAL'365d', NOW()),

(s02, tenant_a, NULL, 'Rosebank Office Park',
 '{"street":"50 Bath Ave","suburb":"Rosebank","city":"Johannesburg","province":"GP","postalCode":"2196"}'::jsonb,
 -26.14530, 28.04290, 'Nandi Dlamini', '+27 11 447 7200',
 'Collect access card from concierge. Patrol basement parking every 2 hours.',
 'a2000002secret32charsseed000001',
 true, 'ACTIVE', '2025-03-01', '2027-02-28', NULL, NULL,
 NULL, false, NULL,
 NOW()-INTERVAL'300d', NOW()),

(s03, tenant_a, NULL, 'Midrand Warehouse District',
 '{"street":"12 Allandale Rd","suburb":"Midrand","city":"Midrand","province":"GP","postalCode":"1685"}'::jsonb,
 -25.99870, 28.12460, 'Henk Botha', '+27 11 312 4400',
 'Armed perimeter. No unauthorized vehicles after 20:00.',
 'a2000003secret32charsseed000001',
 true, 'ACTIVE', '2024-06-01', '2026-05-31', NULL, NULL,
 NULL, false, NULL,
 NOW()-INTERVAL'400d', NOW()),

(s04, tenant_a, NULL, 'Randburg Distribution Centre',
 '{"street":"15 Tungsten Rd","suburb":"Strijdompark","city":"Randburg","province":"GP","postalCode":"2169"}'::jsonb,
 -26.07150, 27.99840, 'Andile Mthembu', '+27 11 462 5500',
 NULL,
 'a2000004secret32charsseed000001',
 false, 'TERMINATED', '2024-01-01', '2025-12-31',
 'Client did not renew — moved operations in-house.',
 NOW()-INTERVAL'30d',
 NULL, false, NULL,
 NOW()-INTERVAL'730d', NOW()-INTERVAL'30d'),

(sb1, tenant_b, NULL, 'Beta Construction HQ',
 '{"street":"1 Builder Ave","suburb":"Pretoria North","city":"Pretoria","province":"GP","postalCode":"0182"}'::jsonb,
 -25.74490, 28.18830, 'Beta Manager', '+27 12 900 0001',
 'Tenant B isolation test site.',
 'a200009bsecret32charsseed000001',
 true, 'ACTIVE', '2025-01-01', '2027-12-31', NULL, NULL,
 NULL, false, NULL,
 NOW(), NOW());


-- ==========================================================================
-- 3. CHECKPOINTS
-- ==========================================================================

INSERT INTO security_checkpoints (
    id, tenant_id, site_id, name, description,
    qr_code, sort_order, active, created_at, updated_at
) VALUES
(c01, tenant_a, s01, 'Main Entrance',    'Ground floor public entrance',     'qrc-a3-sandton-main-000000001', 0, true, NOW()-INTERVAL'365d', NOW()),
(c02, tenant_a, s01, 'Parking Level B1', 'Basement parking level 1',         'qrc-a3-sandton-park-b1-000002', 1, true, NOW()-INTERVAL'365d', NOW()),
(c03, tenant_a, s01, 'North Wing Gate',  'Tenant access gate to North Wing', 'qrc-a3-sandton-nwgate-000003',  2, true, NOW()-INTERVAL'365d', NOW()),
(c04, tenant_a, s01, 'Rooftop Access',   'Restricted — verify door sealed',  'qrc-a3-sandton-roof-0000004',   3, true, NOW()-INTERVAL'365d', NOW()),
(c05, tenant_a, s02, 'Reception',        'Ground floor visitor sign-in',     'qrc-a3-rosebank-recpt-000005',  0, true, NOW()-INTERVAL'300d', NOW()),
(c06, tenant_a, s02, 'Server Room',      'Level 3 — verify badge reader',    'qrc-a3-rosebank-srvr-0000006',  1, true, NOW()-INTERVAL'300d', NOW()),
(c07, tenant_a, s02, 'Parking Deck',     'Open air levels 1-4',              'qrc-a3-rosebank-park-0000007',  2, true, NOW()-INTERVAL'300d', NOW()),
(c08, tenant_a, s03, 'Gate A',           'Main vehicle entry',               'qrc-a3-midrand-gatea-0000008',  0, true, NOW()-INTERVAL'400d', NOW()),
(c09, tenant_a, s03, 'Warehouse Floor',  'Full sweep of aisles',             'qrc-a3-midrand-whflr-0000009',  1, true, NOW()-INTERVAL'400d', NOW()),
(c0a, tenant_a, s03, 'Loading Bay',      'Rear dock — verify shutters',      'qrc-a3-midrand-ldbay-000000a',  2, true, NOW()-INTERVAL'400d', NOW());


-- ==========================================================================
-- 4. SHIFTS
-- ==========================================================================

INSERT INTO security_shifts (
    id, tenant_id, site_id, guard_id,
    start_at, end_at, status, notes, min_scan_count,
    created_at, updated_at
) VALUES
(sh01, tenant_a, s01, g01, today_6am, today_6am+INTERVAL'12h', 'ACTIVE',
 'Day shift. Monitor school holiday crowds.', 2, NOW()-INTERVAL'2d', NOW()),
(sh02, tenant_a, s02, g02, today_6am, today_6am+INTERVAL'8h',  'ACTIVE',
 NULL, 1, NOW()-INTERVAL'3d', NOW()),
(sh03, tenant_a, s03, g08, today_6am, today_6am+INTERVAL'12h', 'ACTIVE',
 'Armed patrol. Dogs active from 22:00.', 3, NOW()-INTERVAL'1d', NOW()),
(sh04, tenant_a, s01, g03, today_22pm, today_22pm+INTERVAL'8h', 'SCHEDULED',
 'Night shift supervisor. Check CCTV feeds at 02:00.', 2, NOW()-INTERVAL'5d', NOW()),
(sh05, tenant_a, s02, g06, tmrw_6am, tmrw_6am+INTERVAL'8h', 'SCHEDULED',
 'Bongani PSiRA renewal pending — confirm docs before deployment.', 1, NOW()-INTERVAL'2d', NOW()),
(sh06, tenant_a, s03, g07, tmrw_6am, tmrw_6am+INTERVAL'12h', 'SCHEDULED',
 'Junior guard — Marco on site for first 2 hours.', 2, NOW()-INTERVAL'1d', NOW()),
(sh07, tenant_a, s01, g01, yest_6am, yest_6am+INTERVAL'12h', 'COMPLETED',
 NULL, 2, NOW()-INTERVAL'3d', NOW()-INTERVAL'2h'),
(sh08, tenant_a, s02, g02, yest_6am, yest_6am+INTERVAL'8h',  'COMPLETED',
 NULL, 1, NOW()-INTERVAL'4d', NOW()-INTERVAL'4h'),
(sh09, tenant_a, s01, g03, two_days_6, two_days_6+INTERVAL'8h', 'COMPLETED',
 'Quiet night — no incidents.', 2, NOW()-INTERVAL'5d', NOW()-INTERVAL'38h'),
(sh0a, tenant_a, s03, g04, two_days_6, two_days_6+INTERVAL'12h', 'MISSED',
 'Guard on approved leave — replacement not arranged.', 0, NOW()-INTERVAL'6d', NOW()-INTERVAL'36h'),
(sh0b, tenant_a, s02, g05, week_ago, week_ago+INTERVAL'8h', 'CANCELLED',
 'Guard suspended before shift.', 0, NOW()-INTERVAL'10d', NOW()-INTERVAL'7d');


-- ==========================================================================
-- 5. CHECKPOINT LOGS
-- ==========================================================================

INSERT INTO security_checkpoint_logs (
    id, tenant_id, checkpoint_id, guard_id, shift_id,
    scanned_at, latitude, longitude, scan_type
) VALUES
-- Active sh01: James at Sandton — 2 scans
(gen_random_uuid(), tenant_a, c01, g01, sh01, today_6am+INTERVAL'30min',  -26.10755, 28.05635, 'QR'),
(gen_random_uuid(), tenant_a, c02, g01, sh01, today_6am+INTERVAL'90min',  -26.10770, 28.05620, 'QR'),
-- Active sh02: Sipho at Rosebank — 1 scan
(gen_random_uuid(), tenant_a, c05, g02, sh02, today_6am+INTERVAL'45min',  -26.14525, 28.04285, 'QR'),
-- Active sh03: Marco at Midrand — 3 scans
(gen_random_uuid(), tenant_a, c08, g08, sh03, today_6am+INTERVAL'20min',  -25.99865, 28.12455, 'QR'),
(gen_random_uuid(), tenant_a, c09, g08, sh03, today_6am+INTERVAL'50min',  -25.99870, 28.12460, 'NFC'),
(gen_random_uuid(), tenant_a, c0a, g08, sh03, today_6am+INTERVAL'80min',  -25.99880, 28.12470, 'QR'),
-- Completed sh07: James at Sandton yesterday
(gen_random_uuid(), tenant_a, c01, g01, sh07, yest_6am+INTERVAL'30min',   -26.10755, 28.05635, 'QR'),
(gen_random_uuid(), tenant_a, c02, g01, sh07, yest_6am+INTERVAL'75min',   -26.10770, 28.05620, 'QR'),
(gen_random_uuid(), tenant_a, c03, g01, sh07, yest_6am+INTERVAL'120min',  -26.10745, 28.05650, 'QR'),
(gen_random_uuid(), tenant_a, c04, g01, sh07, yest_6am+INTERVAL'180min',  -26.10760, 28.05640, 'QR'),
-- Completed sh08: Sipho at Rosebank yesterday
(gen_random_uuid(), tenant_a, c05, g02, sh08, yest_6am+INTERVAL'30min',   -26.14525, 28.04285, 'QR'),
(gen_random_uuid(), tenant_a, c07, g02, sh08, yest_6am+INTERVAL'90min',   -26.14540, 28.04300, 'QR'),
-- Completed sh09: Thandi at Sandton 2 days ago
(gen_random_uuid(), tenant_a, c01, g03, sh09, two_days_6+INTERVAL'40min', -26.10755, 28.05635, 'QR'),
(gen_random_uuid(), tenant_a, c03, g03, sh09, two_days_6+INTERVAL'100min',-26.10745, 28.05650, 'QR');


-- ==========================================================================
-- 6. INCIDENTS
-- ==========================================================================

-- occurred_at = when the incident happened (NOT NULL, no default — must supply)
-- created_at  = when the record was created (has DEFAULT now())
-- acknowledged_by / resolved_by added in V102
INSERT INTO security_incidents (
    id, tenant_id, site_id, shift_id, guard_id,
    title, description, severity, status, type,
    latitude, longitude,
    occurred_at,
    acknowledged_at, acknowledged_by,
    resolved_at, resolved_by,
    created_at, updated_at
) VALUES
(i01, tenant_a, s01, sh01, g01,
 'Unauthorized access - restricted rooftop',
 'Guard found rooftop door forced open at 08:47. Unknown individual fled via fire escape. Lock mechanism damaged.',
 'CRITICAL', 'OPEN', 'TRESPASS',
 -26.10760, 28.05640,
 today_6am+INTERVAL'3h',
 NULL, NULL, NULL, NULL,
 today_6am+INTERVAL'3h', today_6am+INTERVAL'3h'),

(i02, tenant_a, s02, sh02, g02,
 'Laptop theft from Level 2 office',
 'Tenant reported laptop missing from unlocked office. Footage under review. Estimated value R18,000.',
 'HIGH', 'OPEN', 'THEFT',
 -26.14530, 28.04290,
 today_6am+INTERVAL'90min',
 NULL, NULL, NULL, NULL,
 today_6am+INTERVAL'90min', today_6am+INTERVAL'90min'),

(i03, tenant_a, s01, sh07, g01,
 'Graffiti on parking level B1 wall',
 'Extensive graffiti on east wall of Level B1, approximately 15 square metres. Cleaning crew arranged.',
 'MEDIUM', 'ACKNOWLEDGED', 'VANDALISM',
 -26.10770, 28.05620,
 yest_6am+INTERVAL'2h',
 yest_6am+INTERVAL'4h', g08, NULL, NULL,
 yest_6am+INTERVAL'2h', NOW()),

(i04, tenant_a, s03, sh09, g03,
 'Suspicious vehicle parked after hours',
 'White Toyota Hilux without plates outside Gate A from 23:00. Police notified. Vehicle towed at 01:30.',
 'LOW', 'RESOLVED', 'SUSPICIOUS',
 -25.99870, 28.12460,
 two_days_6+INTERVAL'30min',
 two_days_6+INTERVAL'1h', g08,
 two_days_6+INTERVAL'8h', g08,
 two_days_6+INTERVAL'30min', NOW()),

(i05, tenant_a, s01, sh07, g01,
 'Shopper medical emergency - cardiac event',
 'Elderly shopper collapsed near food court. Guard administered CPR. Paramedics arrived in 8 minutes. Patient stable.',
 'MEDIUM', 'RESOLVED', 'MEDICAL',
 -26.10750, 28.05630,
 yest_6am+INTERVAL'15min',
 yest_6am+INTERVAL'30min', g08,
 yest_6am+INTERVAL'2h', g08,
 yest_6am+INTERVAL'15min', NOW()),

(i06, tenant_a, s02, sh08, g02,
 'Physical altercation in parking deck',
 'Two individuals fighting on Level 3. Guard separated parties. One minor injury. Police case CAS 204/06/2026.',
 'HIGH', 'RESOLVED', 'ASSAULT',
 -26.14540, 28.04300,
 yest_6am+INTERVAL'10min',
 yest_6am+INTERVAL'20min', g08,
 yest_6am+INTERVAL'3h', g08,
 yest_6am+INTERVAL'10min', NOW());


-- ==========================================================================
-- 7. AUDIT LOG
-- ==========================================================================

INSERT INTO security_audit_log (
    id, tenant_id, actor_id, actor_type,
    entity_type, entity_id, action,
    old_values, new_values, metadata, occurred_at
) VALUES
(al01, tenant_a, g08, 'USER', 'GUARD', g05, 'STATUS_CHANGED',
 '{"status":"ACTIVE"}'::jsonb,
 '{"status":"SUSPENDED","note":"Pending disciplinary hearing HR-2026-041"}'::jsonb,
 '{"changedBy":"Marco van der Berg"}'::jsonb,
 NOW()-INTERVAL'12d'),

(al02, tenant_a, g08, 'USER', 'SITE', s04, 'TERMINATED',
 '{"contractStatus":"ACTIVE"}'::jsonb,
 '{"contractStatus":"TERMINATED","reason":"Client did not renew."}'::jsonb,
 NULL, NOW()-INTERVAL'30d'),

(al03, tenant_a, g08, 'USER', 'INCIDENT', i03, 'ACKNOWLEDGED',
 '{"status":"OPEN"}'::jsonb,
 '{"status":"ACKNOWLEDGED"}'::jsonb,
 '{"incidentTitle":"Graffiti on parking level B1 wall"}'::jsonb,
 yest_6am+INTERVAL'4h'),

(al04, tenant_a, g08, 'USER', 'SHIFT', sh01, 'CREATED',
 NULL,
 '{"guardId":"a1000000-0000-0000-0000-000000000001","siteId":"a2000000-0000-0000-0000-000000000001"}'::jsonb,
 NULL, NOW()-INTERVAL'2d');

END $$;

COMMIT;

-- VERIFY:
-- SELECT status, COUNT(*) FROM security_guards  WHERE tenant_id='f3ca02b3-eca5-4035-8756-941c72ab6512' AND deleted_at IS NULL GROUP BY status ORDER BY status;
-- SELECT name, contract_status FROM security_sites WHERE tenant_id='f3ca02b3-eca5-4035-8756-941c72ab6512' ORDER BY name;
-- SELECT status, COUNT(*) FROM security_shifts  WHERE tenant_id='f3ca02b3-eca5-4035-8756-941c72ab6512' AND deleted_at IS NULL GROUP BY status ORDER BY status;
-- SELECT severity, status, type FROM security_incidents WHERE tenant_id='f3ca02b3-eca5-4035-8756-941c72ab6512' ORDER BY created_at DESC;