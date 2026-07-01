-- =============================================================================
-- V119__security_phase23_test_data_zeta.sql
-- Phase 2 & 3 Test Data — Zeta Earthmoving (Pty) Ltd
--
-- Tenant: 9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f
--
-- Covers every new table added in V111-V118:
--   Phase 2:  devices, device_sessions, patrol_routes, patrol_rounds,
--             guard_screening_records, resource_custody
--   Phase 3:  armoury, armoury_logs, alarm_events, dispatches, cameras,
--             principals, protection_details, detail_assignments,
--             itinerary_stops, advance_surveys, protection_vehicles
--
-- UUID namespace: c0xxxxxx-0000-0000-0000-xxxxxxxxxxxx
--   c0100000 = devices        c0200000 = sessions
--   c0300000 = patrol routes  c0400000 = patrol rounds
--   c0500000 = armoury        c0600000 = armoury_logs
--   c0700000 = alarm_events   c0800000 = dispatches
--   c0900000 = cameras        c0a00000 = principals
--   c0b00000 = details        c0c00000 = assignments
--   c0d00000 = itinerary      c0e00000 = vehicles
--   c0f00000 = screening      c1000000 = resource_custody
--
-- Existing Zeta UUIDs reused (DO NOT change):
--   Guards:  priya=0708db71, thembisa=9573cec7, sipho=6039c07e, andile=f96bb986
--            g01=b1000000-...-0001  g02=...-0002  g05=...-0005  g06=...-0006
--   Sites:   sandton=acdce7e8  rosebank=ca959145
--            s01=b2000000-...-0001  s02=b2000000-...-0002
--   Shifts:  sh01=b4000000-...-0001 through sh09=...-0009 (from V106)
-- =============================================================================

BEGIN;

DO $$
DECLARE
    tenant   UUID := '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f';

    -- ── Existing guard UUIDs ────────────────────────────────────────────────
    priya    UUID := '0708db71-bf26-4022-9930-464713fec05a';
    thembisa UUID := '9573cec7-7207-4cdc-b9dc-8f747fefa52c';
    sipho    UUID := '6039c07e-b622-431d-9494-4e692c351602';
    andile   UUID := 'f96bb986-aa5c-49fa-96fa-7c8a4b94da57';
    g01      UUID := 'b1000000-0000-0000-0000-000000000001';  -- Nomsa Dlamini
    g02      UUID := 'b1000000-0000-0000-0000-000000000002';  -- Lungelo Mthembu
    g05      UUID := 'b1000000-0000-0000-0000-000000000005';  -- Bafana Khumalo
    g06      UUID := 'b1000000-0000-0000-0000-000000000006';  -- Zanele Vilakazi

    -- ── Existing site UUIDs ─────────────────────────────────────────────────
    sandton  UUID := 'acdce7e8-2557-4dd7-bd9b-70c3cd62e51f';
    rosebank UUID := 'ca959145-6b86-49df-9c73-8310b1a26b86';
    s01      UUID := 'b2000000-0000-0000-0000-000000000001';  -- Centurion Mall
    s02      UUID := 'b2000000-0000-0000-0000-000000000002';  -- Germiston Industrial Park

    -- ── Existing shift UUIDs (from V106) ───────────────────────────────────
    sh01 UUID := 'b4000000-0000-0000-0000-000000000001';
    sh02 UUID := 'b4000000-0000-0000-0000-000000000002';
    sh03 UUID := 'b4000000-0000-0000-0000-000000000003';
    sh07 UUID := 'b4000000-0000-0000-0000-000000000007';

    -- ── Phase 2: Devices (c0100000-...) ────────────────────────────────────
    dev01 UUID := 'c0100000-0000-0000-0000-000000000001';  -- Centurion kiosk tablet
    dev02 UUID := 'c0100000-0000-0000-0000-000000000002';  -- Germiston kiosk tablet
    dev03 UUID := 'c0100000-0000-0000-0000-000000000003';  -- Sandton shared device
    dev04 UUID := 'c0100000-0000-0000-0000-000000000004';  -- Nomsa personal device

    -- ── Phase 2: Device Sessions (c0200000-...) ────────────────────────────
    ses01 UUID := 'c0200000-0000-0000-0000-000000000001';  -- g01 open session
    ses02 UUID := 'c0200000-0000-0000-0000-000000000002';  -- thembisa closed session
    ses03 UUID := 'c0200000-0000-0000-0000-000000000003';  -- andile closed session

    -- ── Phase 2: Patrol Routes (c0300000-...) ──────────────────────────────
    pr01 UUID := 'c0300000-0000-0000-0000-000000000001';  -- Centurion perimeter route
    pr02 UUID := 'c0300000-0000-0000-0000-000000000002';  -- Germiston yard route

    -- ── Phase 2: Patrol Rounds (c0400000-...) ──────────────────────────────
    rnd01 UUID := 'c0400000-0000-0000-0000-000000000001';
    rnd02 UUID := 'c0400000-0000-0000-0000-000000000002';
    rnd03 UUID := 'c0400000-0000-0000-0000-000000000003';
    rnd04 UUID := 'c0400000-0000-0000-0000-000000000004';

    -- ── Phase 2: Guard Screening (c0f00000-...) ────────────────────────────
    scr01 UUID := 'c0f00000-0000-0000-0000-000000000001';
    scr02 UUID := 'c0f00000-0000-0000-0000-000000000002';
    scr03 UUID := 'c0f00000-0000-0000-0000-000000000003';

    -- ── Phase 3: Armoury (c0500000-...) ────────────────────────────────────
    arm01 UUID := 'c0500000-0000-0000-0000-000000000001';  -- Z88 pistol (IN_ARMOURY)
    arm02 UUID := 'c0500000-0000-0000-0000-000000000002';  -- Mossberg shotgun (ISSUED)
    arm03 UUID := 'c0500000-0000-0000-0000-000000000003';  -- Beretta pistol (IN_ARMOURY)
    arm04 UUID := 'c0500000-0000-0000-0000-000000000004';  -- Vektor pistol (ISSUED)

    -- ── Phase 3: Armoury Logs (c0600000-...) ───────────────────────────────
    al01 UUID := 'c0600000-0000-0000-0000-000000000001';
    al02 UUID := 'c0600000-0000-0000-0000-000000000002';
    al03 UUID := 'c0600000-0000-0000-0000-000000000003';

    -- ── Phase 3: Alarm Events (c0700000-...) ───────────────────────────────
    ae01 UUID := 'c0700000-0000-0000-0000-000000000001';  -- CCTV motion — DISPATCHED
    ae02 UUID := 'c0700000-0000-0000-0000-000000000002';  -- Panic button — RESOLVED
    ae03 UUID := 'c0700000-0000-0000-0000-000000000003';  -- Alarm panel — NEW
    ae04 UUID := 'c0700000-0000-0000-0000-000000000004';  -- Manual — FALSE_ALARM

    -- ── Phase 3: Dispatches (c0800000-...) ─────────────────────────────────
    dp01 UUID := 'c0800000-0000-0000-0000-000000000001';  -- Open dispatch on ae01
    dp02 UUID := 'c0800000-0000-0000-0000-000000000002';  -- Resolved dispatch on ae02

    -- ── Phase 3: Cameras (c0900000-...) ────────────────────────────────────
    cam01 UUID := 'c0900000-0000-0000-0000-000000000001';  -- Centurion entrance
    cam02 UUID := 'c0900000-0000-0000-0000-000000000002';  -- Centurion parking
    cam03 UUID := 'c0900000-0000-0000-0000-000000000003';  -- Germiston yard gate
    cam04 UUID := 'c0900000-0000-0000-0000-000000000004';  -- Sandton entrance (OFFLINE)

    -- ── Phase 3: Principals (c0a00000-...) ─────────────────────────────────
    p01 UUID := 'c0a00000-0000-0000-0000-000000000001';  -- CODENAME: EAGLE
    p02 UUID := 'c0a00000-0000-0000-0000-000000000002';  -- CODENAME: FALCON

    -- ── Phase 3: Protection Details (c0b00000-...) ─────────────────────────
    pd01 UUID := 'c0b00000-0000-0000-0000-000000000001';  -- EAGLE MOBILE (ACTIVE)
    pd02 UUID := 'c0b00000-0000-0000-0000-000000000002';  -- FALCON EVENT (PLANNED)

    -- ── Phase 3: Detail Assignments (c0c00000-...) ─────────────────────────
    da01 UUID := 'c0c00000-0000-0000-0000-000000000001';
    da02 UUID := 'c0c00000-0000-0000-0000-000000000002';
    da03 UUID := 'c0c00000-0000-0000-0000-000000000003';

    -- ── Phase 3: Itinerary Stops (c0d00000-...) ────────────────────────────
    is01 UUID := 'c0d00000-0000-0000-0000-000000000001';
    is02 UUID := 'c0d00000-0000-0000-0000-000000000002';
    is03 UUID := 'c0d00000-0000-0000-0000-000000000003';
    is04 UUID := 'c0d00000-0000-0000-0000-000000000004';

    -- ── Phase 3: Protection Vehicles (c0e00000-...) ─────────────────────────
    v01 UUID := 'c0e00000-0000-0000-0000-000000000001';  -- Principal car
    v02 UUID := 'c0e00000-0000-0000-0000-000000000002';  -- Follow car

    -- ── Linked incident from V106 ────────────────────────────────────────────
    i06 UUID := 'b5000000-0000-0000-0000-000000000006';  -- Armed robbery from V106

    -- ── Timestamp anchors ────────────────────────────────────────────────────
    now_ts        TIMESTAMPTZ := NOW();
    today_6am     TIMESTAMPTZ := CURRENT_DATE::TIMESTAMPTZ + INTERVAL'6 hours';
    yest_6am      TIMESTAMPTZ := CURRENT_DATE::TIMESTAMPTZ - INTERVAL'18 hours';
    two_days_6    TIMESTAMPTZ := CURRENT_DATE::TIMESTAMPTZ - INTERVAL'42 hours';

BEGIN

-- =============================================================================
-- 1. DEVICES (Phase 2)
-- =============================================================================

INSERT INTO security_devices (
    id, tenant_id, site_id, device_hardware_id, device_name,
    device_type, kiosk_mode_enabled, status,
    last_seen_at, battery_pct, created_at, updated_at
) VALUES
-- Centurion Mall kiosk tablet (shared, guardhouse)
(dev01, tenant, s01,
 'ANDROID-HUAWEI-CT01-2A3B4C',
 'Centurion Guardhouse Tablet',
 'SHARED_SITE_DEVICE', true, 'ACTIVE',
 now_ts - INTERVAL'4 min', 82,
 now_ts - INTERVAL'30 days', now_ts - INTERVAL'4 min'),

-- Germiston Industrial kiosk
(dev02, tenant, s02,
 'ANDROID-SAMSUNG-GM01-9F8E7D',
 'Germiston Gate A Tablet',
 'SHARED_SITE_DEVICE', true, 'ACTIVE',
 now_ts - INTERVAL'12 min', 61,
 now_ts - INTERVAL'30 days', now_ts - INTERVAL'12 min'),

-- Sandton shared device (battery critical — needs attention)
(dev03, tenant, sandton,
 'ANDROID-XIAOMI-SD01-1A2B3C',
 'Sandton Control Room Device',
 'SHARED_SITE_DEVICE', true, 'ACTIVE',
 now_ts - INTERVAL'2 h', 8,   -- critically low battery
 now_ts - INTERVAL'45 days', now_ts - INTERVAL'2 h'),

-- Nomsa personal device (Enterprise tier)
(dev04, tenant, s01,
 'IPHONE-APPLE-NM01-4D5E6F',
 'Nomsa Dlamini Personal',
 'PERSONAL_GUARD_DEVICE', false, 'ACTIVE',
 now_ts - INTERVAL'1 min', 95,
 now_ts - INTERVAL'20 days', now_ts - INTERVAL'1 min');

-- =============================================================================
-- 2. DEVICE SESSIONS (Phase 2)
-- =============================================================================

INSERT INTO security_device_sessions (
    id, tenant_id, device_id, guard_id, shift_id,
    started_at, ended_at,
    start_pin_verified, start_face_match_confidence, start_geofence_ok,
    end_pin_verified, end_face_match_confidence,
    handover_notes, forced_close_reason, forced_close_by,
    created_at
) VALUES
-- OPEN session: Nomsa currently on shift at Centurion
(ses01, tenant, dev01, g01, sh01,
 today_6am, NULL,
 true, 0.9721, true,
 NULL, NULL,
 NULL, NULL, NULL,
 today_6am),

-- CLOSED session: Thembisa ended shift normally with handover notes
(ses02, tenant, dev03, thembisa, sh02,
 yest_6am, yest_6am + INTERVAL'12 h',
 true, 0.9450, true,
 true, 0.9102,
 'All clear. Attempted card cloning at Gate B at 14:32 — CCTV footage saved. Replacement radio left in guardhouse. Handover complete.',
 NULL, NULL,
 yest_6am),

-- CLOSED session: Andile force-closed by supervisor (forgot to clock out)
(ses03, tenant, dev02, andile, sh03,
 two_days_6, two_days_6 + INTERVAL'13 h 47 min',
 true, NULL, true,
 NULL, NULL,
 NULL,
 'Guard did not clock out. Force-closed by supervisor after incoming guard could not start session.',
 g06,
 two_days_6);

-- =============================================================================
-- 3. PATROL ROUTES (Phase 2)
-- =============================================================================

INSERT INTO security_patrol_routes (
    id, tenant_id, site_id, name,
    interval_minutes, tolerance_minutes,
    active, created_at, updated_at
) VALUES
(pr01, tenant, s01,
 'Centurion Mall Perimeter',
 120, 20,
 true, now_ts - INTERVAL'14 days', now_ts - INTERVAL'14 days'),

(pr02, tenant, s02,
 'Germiston Yard & Warehouse Loop',
 90, 15,
 true, now_ts - INTERVAL'14 days', now_ts - INTERVAL'14 days');

-- =============================================================================
-- 4. PATROL ROUNDS (Phase 2) — for today's active shifts
-- =============================================================================

-- Round 1 (COMPLETE) and Round 2 (IN_PROGRESS) for shift sh01 (Centurion)
INSERT INTO security_patrol_rounds (
    id, tenant_id, site_id, shift_id, route_id,
    round_number, expected_start_at, expected_end_at,
    started_at, completed_at, status,
    checkpoints_expected, checkpoints_scanned,
    off_schedule_reason, created_at, updated_at
) VALUES
(rnd01, tenant, s01, sh01, pr01,
 1, today_6am, today_6am + INTERVAL'2 h',
 today_6am + INTERVAL'3 min', today_6am + INTERVAL'1 h 44 min',
 'COMPLETE', 5, 5,
 NULL,
 today_6am, today_6am + INTERVAL'1 h 44 min'),

(rnd02, tenant, s01, sh01, pr01,
 2, today_6am + INTERVAL'2 h', today_6am + INTERVAL'4 h',
 today_6am + INTERVAL'2 h 8 min', NULL,
 'IN_PROGRESS', 5, 3,
 NULL,
 today_6am, now_ts),

-- Round 1 (MISSED) and Round 2 (EXPECTED) for sh07 at Germiston
-- Round 1 missed: Bafana was late starting
(rnd03, tenant, s02, sh07, pr02,
 1, yest_6am, yest_6am + INTERVAL'1 h 30 min',
 NULL, NULL,
 'MISSED', 4, 0,
 NULL,
 yest_6am, yest_6am + INTERVAL'1 h 30 min'),

-- Round 2 completed despite round 1 miss
(rnd04, tenant, s02, sh07, pr02,
 2, yest_6am + INTERVAL'1 h 30 min', yest_6am + INTERVAL'3 h',
 yest_6am + INTERVAL'1 h 38 min', yest_6am + INTERVAL'2 h 55 min',
 'COMPLETE', 4, 4,
 NULL,
 yest_6am + INTERVAL'1 h 30 min', yest_6am + INTERVAL'2 h 55 min');

-- =============================================================================
-- 5. GUARD SCREENING RECORDS (Phase 2)
-- =============================================================================

INSERT INTO security_guard_screening_records (
    id, tenant_id, guard_id,
    screening_type, reason, result,
    conducted_by, conducted_at, next_due_at,
    report_ref, notes,
    created_by, created_at, updated_at
) VALUES
-- Nomsa — pre-employment polygraph (PASS)
(scr01, tenant, g01,
 'POLYGRAPH', 'ONBOARDING', 'PASS',
 'TruthTec Polygraph Services (Pty) Ltd',
 CURRENT_DATE - INTERVAL'45 days',
 CURRENT_DATE + INTERVAL'2 years' - INTERVAL'45 days',
 'REF-TT-20260515-0091', 'Baseline established. No deception indicated.',
 g06,
 now_ts - INTERVAL'45 days', now_ts - INTERVAL'45 days'),

-- Bafana — criminal record check (PASS, due renewal in 3 years)
(scr02, tenant, g05,
 'CRIMINAL_RECORD_CHECK', 'ONBOARDING', 'PASS',
 'SAPS Clearance Bureau',
 CURRENT_DATE - INTERVAL'60 days',
 CURRENT_DATE + INTERVAL'3 years' - INTERVAL'60 days',
 'SAPS-CRC-2026-BK-4421', NULL,
 g06,
 now_ts - INTERVAL'60 days', now_ts - INTERVAL'60 days'),

-- Andile — drug test after vehicle incident (PENDING — result not yet back)
(scr03, tenant, andile,
 'DRUG_TEST', 'POST_INCIDENT', 'PENDING',
 'MedScreen Labs Midrand',
 NULL, NULL, NULL,
 'Ordered after Incident i04 (vehicle access without authorisation). Sample collected 2026-06-28.',
 g06,
 now_ts - INTERVAL'2 days', now_ts - INTERVAL'2 days');

-- Update guard screening_status rollup
UPDATE security_guards SET screening_status = 'CLEARED'  WHERE id = g01 AND tenant_id = tenant;
UPDATE security_guards SET screening_status = 'CLEARED'  WHERE id = g05 AND tenant_id = tenant;
UPDATE security_guards SET screening_status = 'PENDING'  WHERE id = andile AND tenant_id = tenant;

-- =============================================================================
-- 6. ARMOURY (Phase 3)
-- =============================================================================

INSERT INTO security_armoury (
    id, tenant_id, firearm_serial, firearm_type, make_model,
    saps_license_number, license_issued_at, license_expiry,
    assigned_guard_id, status,
    last_service_at, next_service_due_at,
    notes, created_at, updated_at
) VALUES
-- Z88 pistol — in armoury, good standing
(arm01, tenant,
 'ZA88-2019-004412', 'Handgun', 'Vektor Z88 9mm',
 'SAPS-FCA-2022-JHB-004412', '2022-03-15', '2027-03-14',
 NULL, 'IN_ARMOURY',
 CURRENT_DATE - INTERVAL'180 days', CURRENT_DATE + INTERVAL'185 days',
 'Primary duty pistol. Last cleaned 2026-06-20.', now_ts - INTERVAL'90 days', now_ts),

-- Mossberg shotgun — ISSUED to Zanele for high-risk Sandton deployment
(arm02, tenant,
 'MOSS-590A1-2021-0087', 'Shotgun', 'Mossberg 590A1 12ga',
 'SAPS-FCA-2021-JHB-000087', '2021-09-01', '2026-08-31',
 g06, 'ISSUED',
 CURRENT_DATE - INTERVAL'90 days', CURRENT_DATE + INTERVAL'275 days',
 'Assigned to Zanele for Sandton deployment. License expires 31 Aug 2026 — renewal due.',
 now_ts - INTERVAL'120 days', today_6am),

-- Beretta pistol — in armoury
(arm03, tenant,
 'BRT-92FS-2020-1134', 'Handgun', 'Beretta 92FS 9mm',
 'SAPS-FCA-2020-JHB-001134', '2020-06-10', '2025-06-09',
 NULL, 'IN_ARMOURY',  -- license EXPIRED — issue blocked by service
 CURRENT_DATE - INTERVAL'30 days', CURRENT_DATE + INTERVAL'335 days',
 'LICENSE EXPIRED 2025-06-09. Cannot be issued until renewed.',
 now_ts - INTERVAL'120 days', now_ts),

-- Vektor pistol — ISSUED to Bafana
(arm04, tenant,
 'VEC-CP1-2023-0042', 'Handgun', 'Vektor CP1 9mm',
 'SAPS-FCA-2023-JHB-000042', '2023-11-20', '2028-11-19',
 g05, 'ISSUED',
 CURRENT_DATE - INTERVAL'60 days', CURRENT_DATE + INTERVAL'305 days',
 'Assigned to Bafana Khumalo for armed response duty.',
 now_ts - INTERVAL'60 days', yest_6am);

-- Firearm competency records on guards
UPDATE security_guards
SET firearm_competency_number = 'SAPS-FC-2024-G06-0081',
    firearm_competency_expiry = CURRENT_DATE + INTERVAL'2 years'
WHERE id = g06 AND tenant_id = tenant;

UPDATE security_guards
SET firearm_competency_number = 'SAPS-FC-2024-G05-0094',
    firearm_competency_expiry = CURRENT_DATE + INTERVAL'18 months'
WHERE id = g05 AND tenant_id = tenant;

-- =============================================================================
-- 7. ARMOURY LOGS (Phase 3)
-- =============================================================================

INSERT INTO security_armoury_logs (
    id, tenant_id, armoury_id, guard_id,
    action, witnessed_by_guard_id,
    session_id, shift_id,
    occurred_at, condition_notes, created_at
) VALUES
-- arm02 issued to Zanele (g06), witnessed by Nomsa (g01)
(al01, tenant, arm02, g06,
 'ISSUE', g01,
 ses01, sh02,
 today_6am + INTERVAL'10 min',
 'Clean. Full magazine. Serial verified.',
 today_6am + INTERVAL'10 min'),

-- arm04 issued to Bafana (g05), witnessed by Zanele (g06)
(al02, tenant, arm04, g05,
 'ISSUE', g06,
 NULL, sh03,
 yest_6am + INTERVAL'15 min',
 'Good condition. 15+1 rounds loaded.',
 yest_6am + INTERVAL'15 min'),

-- arm01 previously issued to Thembisa and returned — witness Nomsa
(al03, tenant, arm01, thembisa,
 'RETURN', g01,
 ses02, sh02,
 yest_6am + INTERVAL'12 h 5 min',
 'Returned clean. 2 rounds expended, logged separately. Magazine full after re-load.',
 yest_6am + INTERVAL'12 h 5 min');

-- =============================================================================
-- 8. CAMERAS (Phase 3)
-- =============================================================================

INSERT INTO security_cameras (
    id, tenant_id, site_id, name, provider,
    connection_config, webhook_secret,
    status, last_event_at, notes,
    created_at, updated_at
) VALUES
-- Centurion Mall — Hikvision cloud, active
(cam01, tenant, s01,
 'Main Entrance — North',
 'HIKVISION_CLOUD',
 '{"deviceSerial":"HKVN-CT01-2024","channelId":1,"region":"af-south-1"}'::jsonb,
 'whsec_centurion_north_xk9m2p',
 'ACTIVE', now_ts - INTERVAL'3 min',
 'Covers main entrance turnstiles and drop-off zone.',
 now_ts - INTERVAL'30 days', now_ts - INTERVAL'3 min'),

-- Centurion Mall — parking level (last event was 2h ago — check for fault)
(cam02, tenant, s01,
 'Parking Level P1 — Ramp',
 'HIKVISION_CLOUD',
 '{"deviceSerial":"HKVN-CT02-2024","channelId":2,"region":"af-south-1"}'::jsonb,
 'whsec_centurion_parking_tz4n8q',
 'ACTIVE', now_ts - INTERVAL'2 h 14 min',
 'P1 ramp entry/exit. Motion sensitivity set to medium.',
 now_ts - INTERVAL'30 days', now_ts - INTERVAL'2 h 14 min'),

-- Germiston — ONVIF camera on the yard gate
(cam03, tenant, s02,
 'Yard Gate A — ONVIF',
 'ONVIF',
 '{"host":"192.168.10.45","port":554,"path":"/stream1","username":"admin"}'::jsonb,
 'whsec_germiston_gate_a_bq7r3s',
 'ACTIVE', now_ts - INTERVAL'45 min',
 'ONVIF-compatible dome. Also covers parking bays 1-12.',
 now_ts - INTERVAL'60 days', now_ts - INTERVAL'45 min'),

-- Sandton — OFFLINE (power issue last night)
(cam04, tenant, sandton,
 'Jewellery Row — Level 2',
 'DAHUA_CLOUD',
 '{"deviceId":"DH-SDT-L2-007","apiKey":"[REDACTED]"}'::jsonb,
 'whsec_sandton_lvl2_pf5w1k',
 'OFFLINE', now_ts - INTERVAL'9 h 20 min',
 'Went offline 22:10 last night. Possible power trip on Circuit 7. Tech visit booked for tomorrow.',
 now_ts - INTERVAL'90 days', now_ts - INTERVAL'9 h 20 min');

-- Link cam01 to the CCTV alarm event below
-- (alarm events inserted next, camera_id set via alarm INSERT directly)

-- =============================================================================
-- 9. ALARM EVENTS (Phase 3)
-- =============================================================================

INSERT INTO security_alarm_events (
    id, tenant_id, site_id, source,
    raw_payload, severity, status,
    triggered_by_guard_id,
    latitude, longitude,
    description,
    triaged_by, triaged_at,
    linked_incident_id,
    camera_id,
    created_at, updated_at
) VALUES
-- CCTV motion at Centurion — currently DISPATCHED
(ae01, tenant, s01, 'CCTV_MOTION',
 '{"cameraId":"HKVN-CT01-2024","motionZone":"Zone3","confidence":0.92}'::jsonb,
 'HIGH', 'DISPATCHED',
 NULL, -25.85860, 28.18940,
 'High-confidence motion detected in staff-only corridor after hours. Zone 3 — server room access.',
 g06, today_6am + INTERVAL'23 h 5 min',
 NULL, cam01,
 today_6am + INTERVAL'23 h', today_6am + INTERVAL'23 h 10 min'),

-- Panic button — RESOLVED with linked incident
(ae02, tenant, sandton, 'PANIC_BUTTON',
 NULL,
 'CRITICAL', 'RESOLVED',
 thembisa, -26.10760, 28.05640,
 'Guard panic button activated. Armed response dispatched. Suspects fled on arrival.',
 g06, today_6am + INTERVAL'2 h 1 min',
 i06, NULL,
 today_6am + INTERVAL'2 h', NOW()),

-- Alarm panel — NEW (just landed, not triaged yet)
(ae03, tenant, s02, 'ALARM_PANEL',
 '{"panelId":"DSC-PC1832-GM01","zone":4,"event":"INTRUSION"}'::jsonb,
 'MEDIUM', 'NEW',
 NULL, -26.23080, 28.16720,
 'Zone 4 intrusion trigger — Warehouse Block C perimeter sensor.',
 NULL, NULL, NULL, NULL,
 now_ts - INTERVAL'3 min', now_ts - INTERVAL'3 min'),

-- Manual entry — FALSE ALARM (contractor triggered motion)
(ae04, tenant, s01, 'MANUAL',
 NULL,
 'LOW', 'FALSE_ALARM',
 g01, -25.85860, 28.18940,
 'After-hours movement in loading bay. Identified as authorised contractor working overtime.',
 g06, yest_6am + INTERVAL'23 h 30 min',
 NULL, NULL,
 yest_6am + INTERVAL'23 h 20 min', yest_6am + INTERVAL'23 h 35 min');

-- =============================================================================
-- 10. DISPATCHES (Phase 3)
-- =============================================================================

INSERT INTO security_dispatches (
    id, tenant_id, alarm_event_id,
    dispatched_unit_type, dispatched_guard_id, dispatched_by,
    dispatched_at, arrived_at, resolved_at,
    outcome, resolution_notes, created_at
) VALUES
-- dp01: Open dispatch on ae01 (CCTV motion) — unit en route, arrived on scene
(dp01, tenant, ae01,
 'ARMED_RESPONSE', g06, g06,
 today_6am + INTERVAL'23 h 10 min',
 today_6am + INTERVAL'23 h 19 min',  -- 9 min response time
 NULL,
 NULL, NULL,
 today_6am + INTERVAL'23 h 10 min'),

-- dp02: Resolved dispatch on ae02 (panic button) — suspects fled
(dp02, tenant, ae02,
 'ARMED_RESPONSE', sipho, g06,
 today_6am + INTERVAL'2 h 5 min',
 today_6am + INTERVAL'2 h 12 min',  -- 7 min response time
 today_6am + INTERVAL'2 h 45 min',  -- 33 min total resolution
 'RESOLVED',
 'Two suspects fled before armed response arrived. SAPS case opened: CAS 112/06/2026. CCTV footage handed over.',
 today_6am + INTERVAL'2 h 5 min');

-- =============================================================================
-- 11. PROTECTION VEHICLES (Phase 3 — 9.2)
-- =============================================================================

INSERT INTO security_protection_vehicles (
    id, tenant_id, vehicle_type, registration, make_model,
    armored, assigned_driver_guard_id, status,
    notes, created_at, updated_at
) VALUES
-- VW Phaeton principal car — assigned to Lungelo as driver
(v01, tenant, 'PRINCIPAL_CAR', 'GP 42-37 MF',
 'VW Phaeton 3.0 TDi (2022)',
 true, g02, 'IN_USE',
 'Level IIIA armored. Tinted windows. Run-flat tyres. Pre-check completed 06:00 today.',
 now_ts - INTERVAL'60 days', today_6am),

-- BMW 5 Series follow car — available
(v02, tenant, 'FOLLOW_CAR', 'GP 81-14 KK',
 'BMW 535d xDrive (2023)',
 false, NULL, 'AVAILABLE',
 'Follow vehicle for EAGLE detail. Tracking device installed.',
 now_ts - INTERVAL'60 days', now_ts);

-- =============================================================================
-- 12. PRINCIPALS (Phase 3 — 9.2/9.3)
-- NOTE: medical_notes and known_threats stored as plaintext in test data.
-- In production these would be AES-256-GCM encrypted by FieldEncryptionService.
-- For QA: these values ARE plaintext — encrypt before first real use.
-- =============================================================================

INSERT INTO security_principals (
    id, tenant_id, full_name, alias_codename,
    threat_level, medical_notes, known_threats,
    emergency_contacts, photo_url,
    active, created_at, updated_at
) VALUES
-- EAGLE: High-profile mining executive
(p01, tenant,
 'Dineo Mahlangu', 'EAGLE',
 'HIGH',
 'Hypertensive — carries amlodipine 10mg. EpiPen (peanut allergy, Grade 3). Blood type O+.',
 'Credible threat from former business partner dismissed 2025-11. SAPS case 448/2025. Threat actor: Riaan Botha (wanted). One confirmed surveillance incident at residence (2026-01-14).',
 '[{"name":"Thabo Mahlangu","relationship":"Spouse","phone":"+27821234567"},{"name":"Lindiwe Khumalo","relationship":"EA","phone":"+27837654321"}]'::jsonb,
 NULL, true,
 now_ts - INTERVAL'30 days', now_ts - INTERVAL'30 days'),

-- FALCON: Lower threat, political figure
(p02, tenant,
 'Sello Nkosi', 'FALCON',
 'MEDIUM',
 'No known conditions. Non-smoker.',
 'Elevated social media harassment following 2026 municipal budget speech. No credible physical threat identified. Monitoring ongoing.',
 '[{"name":"Grace Nkosi","relationship":"Spouse","phone":"+27823334444"}]'::jsonb,
 NULL, true,
 now_ts - INTERVAL'14 days', now_ts - INTERVAL'14 days');

-- =============================================================================
-- 13. PROTECTION DETAILS (Phase 3)
-- =============================================================================

INSERT INTO security_protection_details (
    id, tenant_id, principal_id,
    detail_type, start_at, end_at,
    status, billing_rate, client_reference,
    notes, created_at, updated_at
) VALUES
-- EAGLE detail — ACTIVE mobile protection
(pd01, tenant, p01,
 'MOBILE', today_6am, today_6am + INTERVAL'16 h',
 'ACTIVE', 3500.00, 'ZET-CP-2026-047',
 'Board meetings and site visits — Sandton, Rosebank, Centurion. Heightened threat posture. Armed team required.',
 now_ts - INTERVAL'3 days', today_6am),

-- FALCON detail — PLANNED for next week
(pd02, tenant, p02,
 'EVENT', now_ts + INTERVAL'7 days', now_ts + INTERVAL'7 days' + INTERVAL'8 h',
 'PLANNED', 2200.00, 'ZET-CP-2026-051',
 'Midrand Infrastructure Summit. Static and mobile elements. Low threat — standard team sufficient.',
 now_ts - INTERVAL'2 days', now_ts - INTERVAL'2 days');

-- =============================================================================
-- 14. DETAIL ASSIGNMENTS (Phase 3)
-- =============================================================================

INSERT INTO security_detail_assignments (
    id, tenant_id, detail_id, guard_id,
    role, assignment_start, assignment_end,
    vehicle_id, created_at
) VALUES
-- EAGLE detail team
(da01, tenant, pd01, g06,   -- Zanele: Team Leader
 'TEAM_LEADER', today_6am, NULL, NULL, today_6am),
(da02, tenant, pd01, g02,   -- Lungelo: Driver (in the principal car v01)
 'DRIVER', today_6am, NULL, v01, today_6am),
(da03, tenant, pd01, g01,   -- Nomsa: CPO
 'CPO', today_6am, NULL, NULL, today_6am);

-- =============================================================================
-- 15. ITINERARY STOPS (Phase 3)
-- =============================================================================

INSERT INTO security_itinerary_stops (
    id, tenant_id, detail_id, sequence,
    location_name, address,
    latitude, longitude,
    scheduled_arrival, scheduled_departure,
    actual_arrival, actual_departure,
    advance_survey_required, notes,
    created_at, updated_at
) VALUES
-- Stop 1: Residence (COMPLETED — departed)
(is01, tenant, pd01, 1,
 'EAGLE Sandton Residence', '12 Melrose Estate, Sandton, 2196',
 -26.1052, 28.0630,
 today_6am, today_6am + INTERVAL'30 min',
 today_6am + INTERVAL'2 min', today_6am + INTERVAL'28 min',
 true,
 'Secure parking in basement. Access via biometric. TL to lead.',
 now_ts - INTERVAL'3 days', today_6am + INTERVAL'28 min'),

-- Stop 2: First board meeting (COMPLETED — departed)
(is02, tenant, pd01, 2,
 'Sandton Convention Centre — Boardroom 4A', '161 Maude St, Sandown, Sandton',
 -26.1076, 28.0564,
 today_6am + INTERVAL'1 h', today_6am + INTERVAL'4 h',
 today_6am + INTERVAL'58 min', today_6am + INTERVAL'4 h 3 min',
 true,
 'Pre-sweep required 45 min before principal arrival. Venue security briefed.',
 now_ts - INTERVAL'3 days', today_6am + INTERVAL'4 h 3 min'),

-- Stop 3: Lunch (IN_PROGRESS — arrived, not yet departed)
(is03, tenant, pd01, 3,
 'Marble Restaurant, Rosebank', '55 Bath Ave, Rosebank, Johannesburg',
 -26.1486, 28.0414,
 today_6am + INTERVAL'4 h 30 min', today_6am + INTERVAL'6 h',
 today_6am + INTERVAL'4 h 28 min', NULL,
 false,
 'Private dining room reserved. Back entrance preferred for arrival.',
 now_ts - INTERVAL'3 days', today_6am + INTERVAL'4 h 28 min'),

-- Stop 4: Centurion site visit (PENDING)
(is04, tenant, pd01, 4,
 'Zeta Earthmoving Centurion Head Office', '45 Jean Ave, Centurion, 0157',
 -25.8586, 28.1894,
 today_6am + INTERVAL'7 h', today_6am + INTERVAL'14 h',
 NULL, NULL,
 true,
 'Survey required — new site. Advance team to sweep car park and executive floor before 13:00.',
 now_ts - INTERVAL'3 days', now_ts - INTERVAL'3 days');

END $$;

COMMIT;

-- =============================================================================
-- VERIFICATION QUERIES:
-- =============================================================================
-- SELECT COUNT(*), status FROM security_devices    WHERE tenant_id='9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f' GROUP BY status;
-- SELECT COUNT(*), open FROM security_device_sessions WHERE tenant_id='9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f' GROUP BY open;
-- SELECT serial, status, assigned_guard_id FROM security_armoury WHERE tenant_id='9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f';
-- SELECT source, severity, status FROM security_alarm_events WHERE tenant_id='9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f' ORDER BY created_at DESC;
-- SELECT name, status FROM security_cameras WHERE tenant_id='9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f';
-- SELECT alias_codename, threat_level FROM security_principals WHERE tenant_id='9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f';
-- SELECT alias_codename, status, team_size FROM (SELECT pd.id, p.alias_codename, pd.status, COUNT(da.id) AS team_size FROM security_protection_details pd JOIN security_principals p ON p.id=pd.principal_id LEFT JOIN security_detail_assignments da ON da.detail_id=pd.id AND da.assignment_end IS NULL GROUP BY pd.id, p.alias_codename, pd.status) q;
-- SELECT location_name, status FROM security_itinerary_stops WHERE tenant_id='9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f' ORDER BY sequence;