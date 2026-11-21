-- =============================================================================
-- V106__security_test_data_zeta.sql
-- Security Module — QA Seed Data for Zeta Earthmoving (Pty) Ltd
--
-- Tenant: 9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f  (Zeta Earthmoving)
--
-- UUID namespace (b1/b2/b3/b4/b5/b6 prefix — all valid hex, no collision):
--   b1000000-... = guards       b2000000-... = sites
--   b3000000-... = checkpoints  b4000000-... = shifts
--   b5000000-... = incidents    b6000000-... = audit_log
--
-- Existing data NOT touched:
--   Guards:  Priya Govender, Thembisa Mdoda, Sipho Nkosi, Andile Zulu (+ 2 deleted)
--   Sites:   OR Tambo, Rosebank Tower, Sandton City Mall North, kwagga, test
--   Shifts:  02fb1b5b, 6540fd8d, c016d3a6, a720f8fb, 9c279d65
--
-- New guards use PSiRA PSR-2024-9xxxx (avoids PSR-2022-089/445, PSR-2024-112/234)
-- New sites are distinct locations not named in existing data
--
-- Run:
--   DELETE FROM flyway_schema_history WHERE version='106' AND success=false;
--   Get-Content V106__security_test_data_zeta.sql | docker exec -i handyflow-db psql -U handyflow -d handyflow
-- =============================================================================

BEGIN;

DO $$
DECLARE
    tenant UUID := '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f';  -- Zeta Earthmoving

    -- ── Existing guard IDs (for shift/incident references) ────────────────────
    priya   UUID := '0708db71-bf26-4022-9930-464713fec05a';
    thembisa UUID := '9573cec7-7207-4cdc-b9dc-8f747fefa52c';
    sipho   UUID := '6039c07e-b622-431d-9494-4e692c351602';
    andile  UUID := 'f96bb986-aa5c-49fa-96fa-7c8a4b94da57';

    -- ── Existing site IDs (for some new shifts) ────────────────────────────────
    sandton  UUID := 'acdce7e8-2557-4dd7-bd9b-70c3cd62e51f';  -- Sandton City Mall North
    rosebank UUID := 'ca959145-6b86-49df-9c73-8310b1a26b86';  -- Rosebank Tower

    -- ── New guards (b1000000-...)  ────────────────────────────────────────────
    -- PSiRA PSR-2024-9xxxx — no clash with existing PSR-2022-089/445, PSR-2024-112/234
    g01 UUID := 'b1000000-0000-0000-0000-000000000001';  -- Nomsa Dlamini    C  ACTIVE
    g02 UUID := 'b1000000-0000-0000-0000-000000000002';  -- Lungelo Mthembu  D  ACTIVE
    g03 UUID := 'b1000000-0000-0000-0000-000000000003';  -- Kagiso Sithole   C  ON_LEAVE
    g04 UUID := 'b1000000-0000-0000-0000-000000000004';  -- Refilwe Mokoena  D  SUSPENDED
    g05 UUID := 'b1000000-0000-0000-0000-000000000005';  -- Bafana Khumalo   E  ACTIVE (PSiRA 18d)
    g06 UUID := 'b1000000-0000-0000-0000-000000000006';  -- Zanele Vilakazi  B  ACTIVE

    -- ── New sites (b2000000-...) ───────────────────────────────────────────────
    s01 UUID := 'b2000000-0000-0000-0000-000000000001';  -- Centurion Mall
    s02 UUID := 'b2000000-0000-0000-0000-000000000002';  -- Germiston Industrial Park
    s03 UUID := 'b2000000-0000-0000-0000-000000000003';  -- Soweto Plaza (TERMINATED)

    -- ── New checkpoints (b3000000-...) ────────────────────────────────────────
    c01 UUID := 'b3000000-0000-0000-0000-000000000001';
    c02 UUID := 'b3000000-0000-0000-0000-000000000002';
    c03 UUID := 'b3000000-0000-0000-0000-000000000003';
    c04 UUID := 'b3000000-0000-0000-0000-000000000004';
    c05 UUID := 'b3000000-0000-0000-0000-000000000005';
    c06 UUID := 'b3000000-0000-0000-0000-000000000006';

    -- ── New shifts (b4000000-...) ─────────────────────────────────────────────
    sh01 UUID := 'b4000000-0000-0000-0000-000000000001';  -- ACTIVE   Nomsa    Centurion   today
    sh02 UUID := 'b4000000-0000-0000-0000-000000000002';  -- ACTIVE   Andile   Sandton     today
    sh03 UUID := 'b4000000-0000-0000-0000-000000000003';  -- ACTIVE   Zanele   Germiston   today
    sh04 UUID := 'b4000000-0000-0000-0000-000000000004';  -- SCHEDULED Lungelo Centurion   tonight
    sh05 UUID := 'b4000000-0000-0000-0000-000000000005';  -- SCHEDULED Bafana  Germiston   tomorrow
    sh06 UUID := 'b4000000-0000-0000-0000-000000000006';  -- SCHEDULED Priya   Rosebank    tomorrow
    sh07 UUID := 'b4000000-0000-0000-0000-000000000007';  -- COMPLETED Nomsa   Centurion   yesterday
    sh08 UUID := 'b4000000-0000-0000-0000-000000000008';  -- COMPLETED Thembisa Sandton    yesterday
    sh09 UUID := 'b4000000-0000-0000-0000-000000000009';  -- COMPLETED Andile  Germiston   2 days ago
    sh0a UUID := 'b4000000-0000-0000-0000-00000000000a';  -- MISSED   Kagiso  Centurion   2 days ago (on leave)
    sh0b UUID := 'b4000000-0000-0000-0000-00000000000b';  -- CANCELLED Refilwe Germiston  last week

    -- ── New incidents (b5000000-...) ──────────────────────────────────────────
    i01 UUID := 'b5000000-0000-0000-0000-000000000001';  -- OPEN     CRITICAL Centurion  Trespass
    i02 UUID := 'b5000000-0000-0000-0000-000000000002';  -- OPEN     HIGH     Germiston  Theft
    i03 UUID := 'b5000000-0000-0000-0000-000000000003';  -- ACKNOWLEDGED MEDIUM Sandton  Vandalism
    i04 UUID := 'b5000000-0000-0000-0000-000000000004';  -- RESOLVED LOW     Germiston  Suspicious
    i05 UUID := 'b5000000-0000-0000-0000-000000000005';  -- RESOLVED MEDIUM  Centurion  Medical
    i06 UUID := 'b5000000-0000-0000-0000-000000000006';  -- RESOLVED HIGH    Sandton    Assault

    -- ── Audit log (b6000000-...) ──────────────────────────────────────────────
    al01 UUID := 'b6000000-0000-0000-0000-000000000001';
    al02 UUID := 'b6000000-0000-0000-0000-000000000002';
    al03 UUID := 'b6000000-0000-0000-0000-000000000003';

    -- ── Time anchors ──────────────────────────────────────────────────────────
    today_6am  TIMESTAMPTZ := DATE_TRUNC('day', NOW()) + INTERVAL '6 hours';
    today_22pm TIMESTAMPTZ := DATE_TRUNC('day', NOW()) + INTERVAL '22 hours';
    tmrw_6am   TIMESTAMPTZ := DATE_TRUNC('day', NOW()) + INTERVAL '1 day 6 hours';
    yest_6am   TIMESTAMPTZ := DATE_TRUNC('day', NOW()) - INTERVAL '18 hours';
    two_days_6 TIMESTAMPTZ := DATE_TRUNC('day', NOW()) - INTERVAL '42 hours';
    week_ago   TIMESTAMPTZ := NOW() - INTERVAL '7 days';

BEGIN

-- ==========================================================================
-- 1. NEW GUARDS (6 additional for Zeta Earthmoving)
-- ==========================================================================

INSERT INTO security_guards (
    id, tenant_id, first_name, last_name, psira_number, id_number,
    phone, grade, active, status, status_note, status_changed_at,
    psira_expiry_date, notes, created_at, updated_at
) VALUES
(g01, tenant, 'Nomsa',   'Dlamini',
 'PSR-2024-90001', '8901110044080', '+27 82 901 0001', 'C',
 true, 'ACTIVE', NULL, NULL,
 CURRENT_DATE + INTERVAL '20 months', 'Reliable — 3 years at Centurion Mall.',
 NOW()-INTERVAL'280d', NOW()),

(g02, tenant, 'Lungelo', 'Mthembu',
 'PSR-2024-90002', '9404285044083', '+27 71 902 0002', 'D',
 true, 'ACTIVE', NULL, NULL,
 CURRENT_DATE + INTERVAL '11 months', NULL,
 NOW()-INTERVAL'180d', NOW()),

(g03, tenant, 'Kagiso',  'Sithole',
 'PSR-2024-90003', '9612106044086', '+27 83 903 0003', 'C',
 true, 'ON_LEAVE', 'Sick leave — expected return 14 July 2026', NOW()-INTERVAL'3d',
 CURRENT_DATE + INTERVAL '9 months', NULL,
 NOW()-INTERVAL'220d', NOW()),

(g04, tenant, 'Refilwe', 'Mokoena',
 'PSR-2024-90004', '0005116044089', '+27 72 904 0004', 'D',
 true, 'SUSPENDED',
 'Under investigation — access card misuse reported by Centurion site manager.',
 NOW()-INTERVAL'8d',
 CURRENT_DATE + INTERVAL '4 months', NULL,
 NOW()-INTERVAL'150d', NOW()),

-- PSiRA expiring in 18 days — triggers compliance alert
(g05, tenant, 'Bafana',  'Khumalo',
 'PSR-2024-90005', '8512086044082', '+27 71 905 0005', 'E',
 true, 'ACTIVE', NULL, NULL,
 CURRENT_DATE + INTERVAL '18 days',
 'PSiRA renewal in progress. Grade E provisionally renewed.',
 NOW()-INTERVAL'90d', NOW()),

(g06, tenant, 'Zanele',  'Vilakazi',
 'PSR-2024-90006', '9309106044085', '+27 83 906 0006', 'B',
 true, 'ACTIVE', NULL, NULL,
 CURRENT_DATE + INTERVAL '26 months', 'Senior officer. First-aid certified.',
 NOW()-INTERVAL'320d', NOW());


-- ==========================================================================
-- 2. NEW SITES (3 additional for Zeta Earthmoving)
-- ==========================================================================

INSERT INTO security_sites (
    id, tenant_id, customer_id, name, address, latitude, longitude,
    contact_name, contact_phone, instructions,
    qr_secret, active, contract_status, contract_start, contract_end,
    termination_reason, terminated_at,
    portal_token, portal_enabled, portal_label,
    created_at, updated_at
) VALUES
(s01, tenant, NULL, 'Centurion Mall',
 '{"street":"Centurion Rd","suburb":"Centurion","city":"Centurion","province":"GP","postalCode":"0157"}'::jsonb,
 -25.85860, 28.18940, 'Pieter de Wet', '+27 12 643 0000',
 'Night shift: check all emergency exits hourly. Report to security desk at Level 1 West.',
 'b2000001secret32charszeta000001',
 true, 'ACTIVE', '2025-02-01', '2027-01-31', NULL, NULL,
 'portal-centurion-zeta-demo-0001', true,
 'Centurion Mall - Security Dashboard',
 NOW()-INTERVAL'320d', NOW()),

(s02, tenant, NULL, 'Germiston Industrial Park',
 '{"street":"14 Rand Rd","suburb":"Germiston","city":"Ekurhuleni","province":"GP","postalCode":"1401"}'::jsonb,
 -26.23080, 28.16720, 'Themba Nkosi', '+27 11 873 0000',
 'Armed perimeter patrol. No entry after 22:00 without written authorisation. Dogs on site.',
 'b2000002secret32charszeta000001',
 true, 'ACTIVE', '2024-09-01', '2026-08-31', NULL, NULL,
 NULL, false, NULL,
 NOW()-INTERVAL'200d', NOW()),

(s03, tenant, NULL, 'Soweto Plaza',
 '{"street":"Khumalo St","suburb":"Soweto","city":"Johannesburg","province":"GP","postalCode":"1804"}'::jsonb,
 -26.26670, 27.85870, 'Fikile Khumalo', '+27 11 930 0000',
 NULL,
 'b2000003secret32charszeta000001',
 false, 'TERMINATED', '2023-06-01', '2025-05-31',
 'High crime rate made site unviable. Contract not renewed.',
 NOW()-INTERVAL'60d',
 NULL, false, NULL,
 NOW()-INTERVAL'730d', NOW()-INTERVAL'60d');


-- ==========================================================================
-- 3. CHECKPOINTS (Centurion 3, Germiston 3)
-- ==========================================================================

INSERT INTO security_checkpoints (
    id, tenant_id, site_id, name, description,
    qr_code, sort_order, active, created_at, updated_at
) VALUES
(c01, tenant, s01, 'Main Entrance',    'Ground floor main public entrance',   'qrc-b3-centurion-main-000001', 0, true, NOW()-INTERVAL'320d', NOW()),
(c02, tenant, s01, 'Food Court',       'Level 2 food court perimeter',        'qrc-b3-centurion-food-000002', 1, true, NOW()-INTERVAL'320d', NOW()),
(c03, tenant, s01, 'Parking Level P1', 'Underground parking level 1',         'qrc-b3-centurion-park-000003', 2, true, NOW()-INTERVAL'320d', NOW()),
(c04, tenant, s02, 'Gate A',           'Main vehicle gate — verify boom',     'qrc-b3-germiston-gatea-00004', 0, true, NOW()-INTERVAL'200d', NOW()),
(c05, tenant, s02, 'Warehouse Block B','Warehouse B — check all roller doors', 'qrc-b3-germiston-wh-b-00005', 1, true, NOW()-INTERVAL'200d', NOW()),
(c06, tenant, s02, 'Loading Bay',      'Rear loading dock — verify shutters', 'qrc-b3-germiston-ldbay-0006', 2, true, NOW()-INTERVAL'200d', NOW());


-- ==========================================================================
-- 4. SHIFTS (11 new — covers all statuses)
-- ==========================================================================

INSERT INTO security_shifts (
    id, tenant_id, site_id, guard_id,
    start_at, end_at, status, notes, min_scan_count,
    created_at, updated_at
) VALUES
-- ACTIVE (3 on duty right now)
(sh01, tenant, s01, g01, today_6am, today_6am+INTERVAL'12h', 'ACTIVE',
 'Day shift. School holidays — elevated foot traffic.', 2, NOW()-INTERVAL'2d', NOW()),
(sh02, tenant, sandton, andile, today_6am, today_6am+INTERVAL'8h', 'ACTIVE',
 NULL, 1, NOW()-INTERVAL'3d', NOW()),
(sh03, tenant, s02, g06, today_6am, today_6am+INTERVAL'12h', 'ACTIVE',
 'Armed patrol. Gate A must be checked every 90 minutes.', 3, NOW()-INTERVAL'1d', NOW()),

-- SCHEDULED tonight / tomorrow
(sh04, tenant, s01, g02, today_22pm, today_22pm+INTERVAL'8h', 'SCHEDULED',
 'Night shift. Report any suspicious vehicles near P1.', 2, NOW()-INTERVAL'5d', NOW()),
(sh05, tenant, s02, g05, tmrw_6am, tmrw_6am+INTERVAL'12h', 'SCHEDULED',
 'Bafana PSiRA renewal imminent — verify certificate before deployment.', 2, NOW()-INTERVAL'2d', NOW()),
(sh06, tenant, rosebank, priya, tmrw_6am, tmrw_6am+INTERVAL'8h', 'SCHEDULED',
 NULL, 1, NOW()-INTERVAL'1d', NOW()),

-- COMPLETED yesterday / 2 days ago
(sh07, tenant, s01, g01, yest_6am, yest_6am+INTERVAL'12h', 'COMPLETED',
 NULL, 2, NOW()-INTERVAL'3d', NOW()-INTERVAL'2h'),
(sh08, tenant, sandton, thembisa, yest_6am, yest_6am+INTERVAL'8h', 'COMPLETED',
 NULL, 1, NOW()-INTERVAL'4d', NOW()-INTERVAL'4h'),
(sh09, tenant, s02, andile, two_days_6, two_days_6+INTERVAL'12h', 'COMPLETED',
 'All clear — no incidents.', 2, NOW()-INTERVAL'5d', NOW()-INTERVAL'38h'),

-- MISSED — Kagiso on sick leave
(sh0a, tenant, s01, g03, two_days_6, two_days_6+INTERVAL'12h', 'MISSED',
 'Guard on sick leave — no replacement available at short notice.', 0, NOW()-INTERVAL'6d', NOW()-INTERVAL'36h'),

-- CANCELLED — Refilwe suspended
(sh0b, tenant, s02, g04, week_ago, week_ago+INTERVAL'8h', 'CANCELLED',
 'Guard suspended prior to shift. Site covered by supervisor.', 0, NOW()-INTERVAL'10d', NOW()-INTERVAL'7d');


-- ==========================================================================
-- 5. CHECKPOINT LOGS
-- ==========================================================================

INSERT INTO security_checkpoint_logs (
    id, tenant_id, checkpoint_id, guard_id, shift_id,
    scanned_at, latitude, longitude, scan_type
) VALUES
-- Active sh01: Nomsa at Centurion — 2 scans
(gen_random_uuid(), tenant, c01, g01, sh01, today_6am+INTERVAL'30min', -25.85855, 28.18935, 'QR'),
(gen_random_uuid(), tenant, c02, g01, sh01, today_6am+INTERVAL'85min', -25.85870, 28.18950, 'QR'),

-- sh02 (Andile at Sandton) has no scan logs — that site has no checkpoints registered

-- Active sh03: Zanele at Germiston — 3 scans
(gen_random_uuid(), tenant, c04, g06, sh03, today_6am+INTERVAL'20min', -26.23075, 28.16715, 'QR'),
(gen_random_uuid(), tenant, c05, g06, sh03, today_6am+INTERVAL'55min', -26.23085, 28.16725, 'NFC'),
(gen_random_uuid(), tenant, c06, g06, sh03, today_6am+INTERVAL'90min', -26.23090, 28.16730, 'QR'),

-- sh07: Nomsa at Centurion yesterday — full patrol
(gen_random_uuid(), tenant, c01, g01, sh07, yest_6am+INTERVAL'30min',  -25.85855, 28.18935, 'QR'),
(gen_random_uuid(), tenant, c02, g01, sh07, yest_6am+INTERVAL'80min',  -25.85870, 28.18950, 'QR'),
(gen_random_uuid(), tenant, c03, g01, sh07, yest_6am+INTERVAL'130min', -25.85865, 28.18960, 'QR'),

-- sh08 (Thembisa at Sandton) has no scan logs — that site has no checkpoints registered

-- sh09: Andile at Germiston 2 days ago
(gen_random_uuid(), tenant, c04, andile, sh09, two_days_6+INTERVAL'25min', -26.23075, 28.16715, 'QR'),
(gen_random_uuid(), tenant, c05, andile, sh09, two_days_6+INTERVAL'65min', -26.23085, 28.16725, 'QR'),
(gen_random_uuid(), tenant, c06, andile, sh09, two_days_6+INTERVAL'105min',-26.23090, 28.16730, 'QR');


-- ==========================================================================
-- 6. INCIDENTS
-- ==========================================================================

INSERT INTO security_incidents (
    id, tenant_id, site_id, shift_id, guard_id,
    title, description, severity, status, type,
    latitude, longitude,
    occurred_at,
    acknowledged_at, acknowledged_by,
    resolved_at, resolved_by,
    created_at, updated_at
) VALUES
-- OPEN CRITICAL
(i01, tenant, s01, sh01, g01,
 'Tailgating into staff-only zone',
 'Unidentified male followed delivery driver through staff entrance on Level 1. CCTV footage retrieved. Security sweep in progress.',
 'CRITICAL', 'OPEN', 'TRESPASS',
 -25.85860, 28.18940,
 today_6am+INTERVAL'2h',
 NULL, NULL, NULL, NULL,
 today_6am+INTERVAL'2h', today_6am+INTERVAL'2h'),

-- OPEN HIGH
(i02, tenant, s02, sh03, g06,
 'Tools stolen from Warehouse Block B',
 'Angle grinders and power drill set missing from Block B storage. Estimated value R14,500. Gate A access log being reviewed.',
 'HIGH', 'OPEN', 'THEFT',
 -26.23080, 28.16720,
 today_6am+INTERVAL'75min',
 NULL, NULL, NULL, NULL,
 today_6am+INTERVAL'75min', today_6am+INTERVAL'75min'),

-- ACKNOWLEDGED MEDIUM
(i03, tenant, sandton, sh08, thembisa,
 'Shopping trolleys used to block fire exit',
 'Row of trolleys left against fire exit on Level 3. Guard cleared them and reported to mall management. Second occurrence this month.',
 'MEDIUM', 'ACKNOWLEDGED', 'OTHER',
 -26.10760, 28.05640,
 yest_6am+INTERVAL'90min',
 yest_6am+INTERVAL'3h', g06, NULL, NULL,
 yest_6am+INTERVAL'90min', NOW()),

-- RESOLVED LOW
(i04, tenant, s02, sh09, andile,
 'Unfamiliar vehicle in restricted zone after hours',
 'Black bakkie parked inside yard after 21:00 without authorisation. Owner identified as contractor running overtime. Verbal warning issued.',
 'LOW', 'RESOLVED', 'SUSPICIOUS',
 -26.23080, 28.16720,
 two_days_6+INTERVAL'40min',
 two_days_6+INTERVAL'1h', g06,
 two_days_6+INTERVAL'6h', g06,
 two_days_6+INTERVAL'40min', NOW()),

-- RESOLVED MEDIUM
(i05, tenant, s01, sh07, g01,
 'Guard dog bite — member of public',
 'Dog on patrol bit a child who reached through fence at perimeter. First aid administered on site. Parents declined ambulance. Incident report filed.',
 'MEDIUM', 'RESOLVED', 'MEDICAL',
 -25.85860, 28.18940,
 yest_6am+INTERVAL'20min',
 yest_6am+INTERVAL'45min', g06,
 yest_6am+INTERVAL'4h', g06,
 yest_6am+INTERVAL'20min', NOW()),

-- RESOLVED HIGH
(i06, tenant, sandton, sh02, andile,
 'Armed robbery attempt at jewellery store',
 'Two suspects approached jewellery store on Level 2 with concealed weapons. Guard response and panic button activation caused them to flee. SAPS case opened: CAS 112/06/2026.',
 'HIGH', 'RESOLVED', 'ASSAULT',
 -26.10760, 28.05640,
 today_6am+INTERVAL'2h',
 today_6am+INTERVAL'2h30min', g06,
 today_6am+INTERVAL'5h', g06,
 today_6am+INTERVAL'2h', NOW());


-- ==========================================================================
-- 7. AUDIT LOG
-- ==========================================================================

INSERT INTO security_audit_log (
    id, tenant_id, actor_id, actor_type,
    entity_type, entity_id, action,
    old_values, new_values, metadata, occurred_at
) VALUES
(al01, tenant, g06, 'USER', 'GUARD', g04, 'STATUS_CHANGED',
 '{"status":"ACTIVE"}'::jsonb,
 '{"status":"SUSPENDED","note":"Under investigation - access card misuse"}'::jsonb,
 '{"changedBy":"Zanele Vilakazi"}'::jsonb,
 NOW()-INTERVAL'8d'),

(al02, tenant, g06, 'USER', 'SITE', s03, 'TERMINATED',
 '{"contractStatus":"ACTIVE"}'::jsonb,
 '{"contractStatus":"TERMINATED","reason":"High crime rate made site unviable."}'::jsonb,
 NULL, NOW()-INTERVAL'60d'),

(al03, tenant, g06, 'USER', 'INCIDENT', i03, 'ACKNOWLEDGED',
 '{"status":"OPEN"}'::jsonb,
 '{"status":"ACKNOWLEDGED"}'::jsonb,
 '{"incidentTitle":"Shopping trolleys used to block fire exit"}'::jsonb,
 yest_6am+INTERVAL'3h');

END $$;

COMMIT;

-- VERIFY:
-- SELECT COUNT(*), status FROM security_guards  WHERE tenant_id='9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f' AND deleted_at IS NULL GROUP BY status;
-- SELECT name, contract_status FROM security_sites WHERE tenant_id='9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f' ORDER BY name;
-- SELECT COUNT(*), status FROM security_shifts  WHERE tenant_id='9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f' AND deleted_at IS NULL GROUP BY status;
-- SELECT title, severity, status FROM security_incidents WHERE tenant_id='9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f' ORDER BY created_at DESC;