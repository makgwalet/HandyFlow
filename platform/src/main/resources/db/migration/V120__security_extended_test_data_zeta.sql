-- =============================================================================
-- V120__security_extended_test_data_zeta.sql
-- Extended Phase 2 & 3 Test Data — Zeta Earthmoving (Pty) Ltd
--
-- Tenant: 9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f
--
-- Covers:
--   CLOSE PROTECTION: two new principals (HAWK, OSPREY), three new details
--     (HAWK STATIC active, HAWK TRAVEL completed, OSPREY EVENT planned),
--     full team rosters, complete itineraries, advance surveys
--   ADMIN / VETTING: principal_vetting records (CLEARED, HIT, PENDING),
--     vetting_status rollup, one declined principal register entry,
--     CP vetting tiers on guards, audit trail for principal reads
--   CCTV: two additional cameras (Rosebank site), multiple linked alarm
--     events (motion events with camera_id populated), a duress event
--     linked to protection_detail
--   REPORTS: ~90 days of back-dated shift history designed so reports
--     produce meaningful numbers — site coverage rates vary by site,
--     guard attendance has one chronic-miss guard, incident spread
--     across severities
--   RESOURCE CUSTODY: radio and key checkout records for the active
--     CP detail, showing checked-out and returned patterns
--
-- UUID namespace: c2xxxxxx / c3xxxxxx (new block, no collision with V119):
--   c2100000 = principals      c2200000 = details
--   c2300000 = assignments     c2400000 = itinerary stops
--   c2500000 = advance surveys c2600000 = resource custody
--   c2700000 = vetting         c2800000 = alarm events (extended)
--   c2900000 = cameras (new)   c3000000 = dispatches (extended)
--   c3100000 = shifts (history)c3200000 = audit events
-- =============================================================================

BEGIN;

DO $$
DECLARE
    tenant   UUID := '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f';

    -- ── Existing guard UUIDs ──────────────────────────────────────────────────
    priya    UUID := '0708db71-bf26-4022-9930-464713fec05a';
    thembisa UUID := '9573cec7-7207-4cdc-b9dc-8f747fefa52c';
    sipho    UUID := '6039c07e-b622-431d-9494-4e692c351602';
    andile   UUID := 'f96bb986-aa5c-49fa-96fa-7c8a4b94da57';
    g01      UUID := 'b1000000-0000-0000-0000-000000000001';  -- Nomsa Dlamini
    g02      UUID := 'b1000000-0000-0000-0000-000000000002';  -- Lungelo Mthembu
    g03      UUID := 'b1000000-0000-0000-0000-000000000003';  -- Kagiso Sithole
    g05      UUID := 'b1000000-0000-0000-0000-000000000005';  -- Bafana Khumalo
    g06      UUID := 'b1000000-0000-0000-0000-000000000006';  -- Zanele Vilakazi

    -- ── Existing site UUIDs ───────────────────────────────────────────────────
    sandton  UUID := 'acdce7e8-2557-4dd7-bd9b-70c3cd62e51f';
    rosebank UUID := 'ca959145-6b86-49df-9c73-8310b1a26b86';
    s01      UUID := 'b2000000-0000-0000-0000-000000000001';  -- Centurion Mall
    s02      UUID := 'b2000000-0000-0000-0000-000000000002';  -- Germiston Industrial

    -- ── Existing device/session UUIDs ─────────────────────────────────────────
    dev01 UUID := 'c0100000-0000-0000-0000-000000000001';
    dev02 UUID := 'c0100000-0000-0000-0000-000000000002';
    ses01 UUID := 'c0200000-0000-0000-0000-000000000001';  -- Nomsa open session

    -- ── Existing V119 UUIDs ───────────────────────────────────────────────────
    p01   UUID := 'c0a00000-0000-0000-0000-000000000001';  -- EAGLE
    p02   UUID := 'c0a00000-0000-0000-0000-000000000002';  -- FALCON
    pd01  UUID := 'c0b00000-0000-0000-0000-000000000001';  -- EAGLE MOBILE (ACTIVE)
    is03  UUID := 'c0d00000-0000-0000-0000-000000000003';  -- EAGLE stop 3 (IN_PROGRESS)
    is04  UUID := 'c0d00000-0000-0000-0000-000000000004';  -- EAGLE stop 4 (PENDING)
    v01   UUID := 'c0e00000-0000-0000-0000-000000000001';  -- Principal car
    v02   UUID := 'c0e00000-0000-0000-0000-000000000002';  -- Follow car
    cam01 UUID := 'c0900000-0000-0000-0000-000000000001';  -- Centurion entrance
    cam02 UUID := 'c0900000-0000-0000-0000-000000000002';  -- Centurion parking
    ae01  UUID := 'c0700000-0000-0000-0000-000000000001';  -- CCTV motion DISPATCHED
    dp01  UUID := 'c0800000-0000-0000-0000-000000000001';  -- Open dispatch on ae01

    -- ── New principals (c2100000-...) ─────────────────────────────────────────
    p03 UUID := 'c2100000-0000-0000-0000-000000000001';  -- HAWK
    p04 UUID := 'c2100000-0000-0000-0000-000000000002';  -- OSPREY
    p05 UUID := 'c2100000-0000-0000-0000-000000000003';  -- RAVEN (declined)

    -- ── New protection details (c2200000-...) ─────────────────────────────────
    pd03 UUID := 'c2200000-0000-0000-0000-000000000001';  -- HAWK STATIC (ACTIVE)
    pd04 UUID := 'c2200000-0000-0000-0000-000000000002';  -- HAWK TRAVEL (COMPLETED)
    pd05 UUID := 'c2200000-0000-0000-0000-000000000003';  -- OSPREY EVENT (PLANNED)
    pd06 UUID := 'c2200000-0000-0000-0000-000000000004';  -- FALCON MOBILE (ACTIVE)

    -- ── New detail assignments (c2300000-...) ─────────────────────────────────
    da04 UUID := 'c2300000-0000-0000-0000-000000000001';  -- HAWK: Andile TL
    da05 UUID := 'c2300000-0000-0000-0000-000000000002';  -- HAWK: Sipho CPO
    da06 UUID := 'c2300000-0000-0000-0000-000000000003';  -- HAWK: Priya Driver
    da07 UUID := 'c2300000-0000-0000-0000-000000000004';  -- FALCON: Kagiso CPO
    da08 UUID := 'c2300000-0000-0000-0000-000000000005';  -- FALCON: Bafana TL

    -- ── New itinerary stops (c2400000-...) ────────────────────────────────────
    is05 UUID := 'c2400000-0000-0000-0000-000000000001';  -- HAWK stop 1 (COMPLETED)
    is06 UUID := 'c2400000-0000-0000-0000-000000000002';  -- HAWK stop 2 (IN_PROGRESS)
    is07 UUID := 'c2400000-0000-0000-0000-000000000003';  -- HAWK stop 3 (PENDING)
    is08 UUID := 'c2400000-0000-0000-0000-000000000004';  -- OSPREY stop 1 (PENDING)
    is09 UUID := 'c2400000-0000-0000-0000-000000000005';  -- OSPREY stop 2 (PENDING)
    is10 UUID := 'c2400000-0000-0000-0000-000000000006';  -- FALCON stop 1 (COMPLETED)

    -- ── Advance surveys (c2500000-...) ────────────────────────────────────────
    srv01 UUID := 'c2500000-0000-0000-0000-000000000001';  -- EAGLE stop 4 survey (ALL_CLEAR)
    srv02 UUID := 'c2500000-0000-0000-0000-000000000002';  -- HAWK stop 2 survey (NOT all clear)
    srv03 UUID := 'c2500000-0000-0000-0000-000000000003';  -- HAWK stop 2 second survey (ALL_CLEAR)

    -- ── Resource custody (c2600000-...) ───────────────────────────────────────
    rc01 UUID := 'c2600000-0000-0000-0000-000000000001';  -- Radio checked out
    rc02 UUID := 'c2600000-0000-0000-0000-000000000002';  -- Radio returned
    rc03 UUID := 'c2600000-0000-0000-0000-000000000003';  -- Key set out
    rc04 UUID := 'c2600000-0000-0000-0000-000000000004';  -- Vehicle custody

    -- ── Principal vetting (c2700000-...) ──────────────────────────────────────
    pv01 UUID := 'c2700000-0000-0000-0000-000000000001';  -- HAWK sanctions CLEAR
    pv02 UUID := 'c2700000-0000-0000-0000-000000000002';  -- HAWK PEP CLEAR
    pv03 UUID := 'c2700000-0000-0000-0000-000000000003';  -- OSPREY sanctions CLEAR
    pv04 UUID := 'c2700000-0000-0000-0000-000000000004';  -- FALCON adverse media HIT
    pv05 UUID := 'c2700000-0000-0000-0000-000000000005';  -- RAVEN sanctions HIT
    pv06 UUID := 'c2700000-0000-0000-0000-000000000006';  -- EAGLE source-of-funds PENDING

    -- ── New alarm events (c2800000-...) ───────────────────────────────────────
    ae05 UUID := 'c2800000-0000-0000-0000-000000000001';  -- CCTV Rosebank motion
    ae06 UUID := 'c2800000-0000-0000-0000-000000000002';  -- DURESS on HAWK detail
    ae07 UUID := 'c2800000-0000-0000-0000-000000000003';  -- Rosebank alarm panel
    ae08 UUID := 'c2800000-0000-0000-0000-000000000004';  -- CCTV Centurion parking

    -- ── New cameras at Rosebank (c2900000-...) ────────────────────────────────
    cam05 UUID := 'c2900000-0000-0000-0000-000000000001';  -- Rosebank lobby
    cam06 UUID := 'c2900000-0000-0000-0000-000000000002';  -- Rosebank basement

    -- ── New dispatches (c3000000-...) ─────────────────────────────────────────
    dp03 UUID := 'c3000000-0000-0000-0000-000000000001';  -- DURESS dispatch
    dp04 UUID := 'c3000000-0000-0000-0000-000000000002';  -- Rosebank dispatch

    -- ── Declined principal register ────────────────────────────────────────────
    dec01 UUID := 'c2100000-0000-0000-0000-000000000099';

    -- ── Audit events (c3200000-...) ────────────────────────────────────────────
    aud01 UUID := 'c3200000-0000-0000-0000-000000000001';
    aud02 UUID := 'c3200000-0000-0000-0000-000000000002';
    aud03 UUID := 'c3200000-0000-0000-0000-000000000003';
    aud04 UUID := 'c3200000-0000-0000-0000-000000000004';

    -- ── Timestamp anchors ─────────────────────────────────────────────────────
    now_ts     TIMESTAMPTZ := NOW();
    today_6am  TIMESTAMPTZ := CURRENT_DATE::TIMESTAMPTZ + INTERVAL'6 hours';
    yest_6am   TIMESTAMPTZ := CURRENT_DATE::TIMESTAMPTZ - INTERVAL'18 hours';
    d2_6am     TIMESTAMPTZ := CURRENT_DATE::TIMESTAMPTZ - INTERVAL'42 hours';
    d7_6am     TIMESTAMPTZ := CURRENT_DATE::TIMESTAMPTZ - INTERVAL'162 hours';
    d14_6am    TIMESTAMPTZ := CURRENT_DATE::TIMESTAMPTZ - INTERVAL'330 hours';
    d30_6am    TIMESTAMPTZ := CURRENT_DATE::TIMESTAMPTZ - INTERVAL'714 hours';
    d60_6am    TIMESTAMPTZ := CURRENT_DATE::TIMESTAMPTZ - INTERVAL'1434 hours';
    d90_6am    TIMESTAMPTZ := CURRENT_DATE::TIMESTAMPTZ - INTERVAL'2154 hours';

BEGIN

-- =============================================================================
-- 1. THREE NEW PRINCIPALS
-- =============================================================================

INSERT INTO security_principals (
    id, tenant_id, full_name, alias_codename,
    threat_level, medical_notes, known_threats,
    emergency_contacts, photo_url,
    active, created_at, updated_at
) VALUES
-- HAWK: CRITICAL threat — government official under active protection order
(p03, tenant,
 'Vincent Dlamini', 'HAWK',
 'CRITICAL',
 'Type 2 diabetic — carries insulin pen and glucose gel. Penicillin allergy (severe). Blood group A+. No physical limitations.',
 'Protection order granted Gauteng High Court 2026-02-14. Threat actor: Organised crime syndicate linked to tender fraud case (SCCU docket 14/2026). Two prior vehicular intimidation incidents (2026-01-09 and 2026-03-22). Considered armed and dangerous. SAPS intelligence briefing ref: GH-SIU-2026-0441.',
 '[{"name":"Patricia Dlamini","relationship":"Spouse","phone":"+27829876543"},{"name":"Raymond Sithole","relationship":"Attorney","phone":"+27112234567"}]'::jsonb,
 NULL, true,
 d30_6am, d30_6am),

-- OSPREY: MEDIUM threat — celebrity at elevated risk ahead of public event
(p04, tenant,
 'Thandi Mokoena', 'OSPREY',
 'MEDIUM',
 'No known conditions. Carries epinephrine auto-injector (bee sting allergy). Non-smoker.',
 'Elevated stalking risk identified following viral social media incident 2026-05-12. Known individual (TRO issued 2026-05-20). No credible physical weapons threat identified.',
 '[{"name":"Sipho Mokoena","relationship":"Husband","phone":"+27831122334"},{"name":"Kate Burger","relationship":"Manager","phone":"+27215566778"}]'::jsonb,
 NULL, true,
 d14_6am, d14_6am),

-- RAVEN: DECLINED — AML/sanctions hit means company refused engagement
(p05, tenant,
 'Piet van der Merwe', 'RAVEN',
 'LOW',
 NULL,
 NULL,
 NULL,
 NULL, false,  -- deactivated upon declination
 d60_6am, d60_6am);

-- =============================================================================
-- 2. FOUR NEW PROTECTION DETAILS
-- =============================================================================

INSERT INTO security_protection_details (
    id, tenant_id, principal_id,
    detail_type, start_at, end_at,
    status, billing_rate, client_reference,
    notes, created_at, updated_at
) VALUES
-- HAWK STATIC at a safe house — ACTIVE
(pd03, tenant, p03,
 'STATIC', today_6am - INTERVAL'2 days', today_6am + INTERVAL'5 days',
 'ACTIVE', 4800.00, 'ZET-CP-2026-052',
 'Safe house protection — Midrand. Fixed post. No movement planned. 24/7 armed team. CRITICAL clearance required for all assigned officers.',
 d7_6am, today_6am - INTERVAL'2 days'),

-- HAWK TRAVEL — COMPLETED last week (for report data)
(pd04, tenant, p03,
 'TRAVEL', d14_6am, d14_6am + INTERVAL'10 h',
 'COMPLETED', 4200.00, 'ZET-CP-2026-039',
 'ORTIA departure to Cape Town. Motorcade + airport escort. No incidents.',
 d14_6am - INTERVAL'3 days', d14_6am + INTERVAL'10 h'),

-- OSPREY EVENT — upcoming Summit in 7 days
(pd05, tenant, p04,
 'EVENT', now_ts + INTERVAL'5 days', now_ts + INTERVAL'5 days' + INTERVAL'6 h',
 'PLANNED', 2600.00, 'ZET-CP-2026-058',
 'Sandton Dome music event. Mixed crowd ~12,000. Dedicated escort from arrival through backstage to stage and back.',
 d7_6am, d7_6am),

-- FALCON MOBILE — second active detail for FALCON (different days from pd02)
(pd06, tenant, p02,
 'MOBILE', today_6am, today_6am + INTERVAL'8 h',
 'ACTIVE', 2400.00, 'ZET-CP-2026-055',
 'Johannesburg CBD ward visits. Medium threat. Standard 2-person team.',
 d2_6am, today_6am);

-- =============================================================================
-- 3. TEAM ASSIGNMENTS — all four new details
-- =============================================================================

INSERT INTO security_detail_assignments (
    id, tenant_id, detail_id, guard_id,
    role, assignment_start, assignment_end,
    vehicle_id, created_at
) VALUES
-- HAWK STATIC: Andile TL, Sipho CPO, Priya Driver (CRITICAL tier required)
(da04, tenant, pd03, andile, 'TEAM_LEADER',   today_6am - INTERVAL'2 days', NULL, NULL,     today_6am - INTERVAL'2 days'),
(da05, tenant, pd03, sipho,  'CPO',           today_6am - INTERVAL'2 days', NULL, NULL,     today_6am - INTERVAL'2 days'),
(da06, tenant, pd03, priya,  'DRIVER',        today_6am - INTERVAL'2 days', NULL, v02,      today_6am - INTERVAL'2 days'),

-- FALCON MOBILE today: Kagiso CPO, Bafana TL
(da07, tenant, pd06, g03,    'CPO',           today_6am, NULL, NULL,                        today_6am),
(da08, tenant, pd06, g05,    'TEAM_LEADER',   today_6am, NULL, NULL,                        today_6am);

-- Set CRITICAL vetting tier on Andile and Sipho for HAWK assignment
UPDATE security_guards
SET cp_vetting_tier       = 'CRITICAL',
    cp_vetting_cleared_at = CURRENT_DATE - INTERVAL'60 days',
    cp_vetting_expires_at  = CURRENT_DATE + INTERVAL'18 months'
WHERE id = andile AND tenant_id = tenant;

UPDATE security_guards
SET cp_vetting_tier       = 'HIGH',
    cp_vetting_cleared_at = CURRENT_DATE - INTERVAL'45 days',
    cp_vetting_expires_at  = CURRENT_DATE + INTERVAL'2 years'
WHERE id = sipho AND tenant_id = tenant;

UPDATE security_guards
SET cp_vetting_tier       = 'STANDARD',
    cp_vetting_cleared_at = CURRENT_DATE - INTERVAL'30 days',
    cp_vetting_expires_at  = CURRENT_DATE + INTERVAL'1 year'
WHERE id = g03 AND tenant_id = tenant;

-- =============================================================================
-- 4. ITINERARY STOPS — three details with varied status
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
-- HAWK STATIC (pd03) — single fixed post, treated as one continuous stop
(is05, tenant, pd03, 1,
 'Midrand Safe House — Unit 4', '48 Bekker Road, Vorna Valley, Midrand',
 -25.9978, 28.1239,
 today_6am - INTERVAL'2 days', today_6am + INTERVAL'5 days',
 today_6am - INTERVAL'2 days' + INTERVAL'4 min', NULL,
 true,
 'Static post. Guards rotate 12h shifts. Perimeter secure. No visitors without pre-clearance.',
 d7_6am, today_6am - INTERVAL'2 days' + INTERVAL'4 min'),

-- HAWK STATIC stop 2 — planned medical clinic visit (PENDING — 3 days away)
(is06, tenant, pd03, 2,
 'Mediclinic Midrand — Room 204',
 '1 Corobrick Rd, Halfway Gardens, Midrand',
 -25.9865, 28.1301,
 today_6am + INTERVAL'3 days' + INTERVAL'10 h',
 today_6am + INTERVAL'3 days' + INTERVAL'12 h',
 NULL, NULL,
 true,
 'Routine specialist appointment. Advance team to sweep corridor and waiting room 30 min prior. Use service entrance.',
 d7_6am, d7_6am),

-- HAWK STATIC stop 3 — court appearance next week (PENDING)
(is07, tenant, pd03, 3,
 'Gauteng High Court — Johannesburg',
 'Pritchard St & Von Brandis St, Johannesburg, 2001',
 -26.2041, 28.0473,
 today_6am + INTERVAL'6 days' + INTERVAL'8 h',
 today_6am + INTERVAL'6 days' + INTERVAL'13 h',
 NULL, NULL,
 true,
 'High risk — court appearance in SCCU case. Coordinate with SAPS escort. CRITICAL priority.',
 d7_6am, d7_6am),

-- OSPREY EVENT (pd05) — two stops
(is08, tenant, pd05, 1,
 'OSPREY Soweto Residence', '7 Naledi Ave, Meadowlands, Soweto',
 -26.2487, 27.9003,
 now_ts + INTERVAL'5 days' + INTERVAL'15 h',
 now_ts + INTERVAL'5 days' + INTERVAL'15 h 45 min',
 NULL, NULL,
 false,
 'Collect from residence. Perimeter check only — no sweep required.',
 d7_6am, d7_6am),

(is09, tenant, pd05, 2,
 'Sandton Dome — Backstage Entrance', '83 Rivonia Rd, Sandhurst, Sandton',
 -26.1074, 28.0576,
 now_ts + INTERVAL'5 days' + INTERVAL'16 h',
 now_ts + INTERVAL'5 days' + INTERVAL'22 h',
 NULL, NULL,
 true,
 'Full sweep of backstage, green room, and stage-left access before principal arrival. Coordinate with venue security.',
 d7_6am, d7_6am),

-- FALCON MOBILE today (pd06) — one completed stop
(is10, tenant, pd06, 1,
 'Ward 88 Community Hall — Diepsloot', '14 Freedom Rd, Diepsloot, Johannesburg',
 -25.9285, 28.0082,
 today_6am + INTERVAL'1 h', today_6am + INTERVAL'3 h',
 today_6am + INTERVAL'58 min', today_6am + INTERVAL'3 h 5 min',
 false,
 'Public meeting — 200 attendees expected. Low risk. Guard at door and one roaming.',
 d2_6am, today_6am + INTERVAL'3 h 5 min');

-- =============================================================================
-- 5. ADVANCE SURVEYS
-- =============================================================================

INSERT INTO security_advance_surveys (
    id, tenant_id, itinerary_stop_id,
    surveyed_by_guard_id, surveyed_at,
    entry_exit_routes_notes, hazards_noted,
    photo_urls, all_clear, created_at
) VALUES
-- EAGLE stop 4 (Centurion HQ) — ALL CLEAR, Nomsa surveyed it
(srv01, tenant, is04, g01,
 today_6am + INTERVAL'5 h 30 min',
 'Main entrance: 2 revolving doors, ramp access on east side. Executive parking: 8 bays, barrier-controlled. Service road at rear usable for discreet arrival.',
 'One unfamiliar vehicle (grey Fortuner GP 23-14 XK) in bay 7 — noted for monitoring. No other concerns.',
 '["https://cdn.handyflow.app/surveys/s001-bay.jpg","https://cdn.handyflow.app/surveys/s001-lobby.jpg"]'::jsonb,
 true,
 today_6am + INTERVAL'5 h 30 min'),

-- HAWK stop 2 (Mediclinic) — first survey NOT all clear (unfamiliar vehicle near entrance)
(srv02, tenant, is06, sipho,
 today_6am + INTERVAL'3 days' + INTERVAL'8 h',
 'Service entrance on north side — key code required. Lift bank A to second floor. Room 204 at end of corridor, no windows.',
 'White bakkie GP 78-92 KN parked facing entrance with engine running for >10 minutes. Driver unresponsive. Suspicious.',
 '["https://cdn.handyflow.app/surveys/s002-corridor.jpg"]'::jsonb,
 false,  -- NOT all clear — requires follow-up survey
 today_6am + INTERVAL'3 days' + INTERVAL'8 h'),

-- HAWK stop 2 — second survey by Andile after suspicious vehicle cleared (ALL CLEAR)
(srv03, tenant, is06, andile,
 today_6am + INTERVAL'3 days' + INTERVAL'9 h',
 'Same as prior survey. Vehicle confirmed as medical courier — driver identified and confirmed with clinic reception.',
 'No hazards noted. Courier vehicle resolved. All clear for principal approach.',
 '["https://cdn.handyflow.app/surveys/s003-cleared.jpg"]'::jsonb,
 true,
 today_6am + INTERVAL'3 days' + INTERVAL'9 h');

-- =============================================================================
-- 6. RESOURCE CUSTODY (Phase 2) — radio and key checkout for CP detail
-- =============================================================================

INSERT INTO security_resource_custody (
    id, tenant_id, session_id, guard_id, shift_id,
    resource_type, resource_ref, resource_id,
    checked_out_at, checked_in_at,
    witnessed_by, checkout_notes, checkin_notes,
    condition_on_return, created_at
) VALUES
-- Radio R-007 checked out to Nomsa for EAGLE detail — still out
(rc01, tenant, ses01, g01, NULL,
 'RADIO', 'Radio R-007 (Hytera PD785)', NULL,
 today_6am + INTERVAL'5 min', NULL,
 g06, 'Full charge. Programmed on Channel 4 (CP ops).',
 NULL, NULL,
 today_6am + INTERVAL'5 min'),

-- Radio R-003 checked out and returned by Thembisa yesterday — use ses02 (Thembisa's closed session)
(rc02, tenant, 'c0200000-0000-0000-0000-000000000002'::uuid, thembisa, NULL,
 'RADIO', 'Radio R-003 (Hytera PD785)', NULL,
 yest_6am + INTERVAL'10 min',
 yest_6am + INTERVAL'12 h 5 min',
 g01, 'Checked out for Sandton patrol shift.',
 'Returned in good condition. Battery at 40%.',
 'GOOD',
 yest_6am + INTERVAL'10 min'),

-- Key set (Germiston warehouse) checked out to Andile — use ses03 (Andile's closed session)
(rc03, tenant, 'c0200000-0000-0000-0000-000000000003'::uuid, andile, NULL,
 'KEY', 'Germiston Warehouse Key Set B (6 keys)', NULL,
 today_6am + INTERVAL'30 min', NULL,
 sipho, 'Includes master, Block B x3, generator room, safe key.',
 NULL, NULL,
 today_6am + INTERVAL'30 min'),

-- Vehicle custody record for the EAGLE principal car
(rc04, tenant, ses01, g02, NULL,
 'VEHICLE', 'GP 42-37 MF (VW Phaeton)', 'c0e00000-0000-0000-0000-000000000001'::uuid,
 today_6am + INTERVAL'1 min', NULL,
 g06, 'Full fuel. Pre-drive safety check passed. Dashcam operational.',
 NULL, NULL,
 today_6am + INTERVAL'1 min');

-- =============================================================================
-- 7. NEW CAMERAS AT ROSEBANK
-- =============================================================================

INSERT INTO security_cameras (
    id, tenant_id, site_id, name, provider,
    connection_config, webhook_secret,
    status, last_event_at, notes,
    created_at, updated_at
) VALUES
(cam05, tenant, rosebank,
 'Lobby — Main Reception',
 'RTSP_GENERIC',
 '{"host":"192.168.20.10","port":554,"path":"/cam1/stream","codec":"H264"}'::jsonb,
 'whsec_rosebank_lobby_mn7q4r',
 'ACTIVE', now_ts - INTERVAL'8 min',
 'Covers reception desk and main lift lobby. Motion threshold medium.',
 d30_6am, now_ts - INTERVAL'8 min'),

(cam06, tenant, rosebank,
 'Basement Parking — Level B2',
 'RTSP_GENERIC',
 '{"host":"192.168.20.11","port":554,"path":"/cam2/stream","codec":"H264"}'::jsonb,
 'whsec_rosebank_b2_xp2n6w',
 'ACTIVE', now_ts - INTERVAL'22 min',
 'Low-light IR mode active after 19:00.',
 d30_6am, now_ts - INTERVAL'22 min');

-- =============================================================================
-- 8. ADDITIONAL ALARM EVENTS — CCTV, DURESS, PANEL
-- =============================================================================

INSERT INTO security_alarm_events (
    id, tenant_id, site_id, source,
    raw_payload, severity, status,
    triggered_by_guard_id,
    latitude, longitude, description,
    triaged_by, triaged_at,
    linked_incident_id, camera_id, protection_detail_id,
    created_at, updated_at
) VALUES

-- Rosebank CCTV motion — currently TRIAGED awaiting dispatch decision
(ae05, tenant, rosebank, 'CCTV_MOTION',
 '{"cameraId":"RTSP-RB01","motionZone":"LiftLobby","confidence":0.87}'::jsonb,
 'MEDIUM', 'TRIAGED',
 NULL, -26.1486, 28.0414,
 'Sustained motion in lift lobby at 02:14. Three individuals not matching tenant profiles. Building security notified.',
 g06, now_ts - INTERVAL'18 min',
 NULL, cam05, NULL,
 now_ts - INTERVAL'20 min', now_ts - INTERVAL'18 min'),

-- DURESS event from Andile on the HAWK detail — DISPATCHED
(ae06, tenant, NULL, 'DURESS',
 NULL,
 'CRITICAL', 'DISPATCHED',
 andile, -25.9978, 28.1239,
 'DURESS TRIGGER — immediate response required',
 g06, today_6am - INTERVAL'1 day' + INTERVAL'14 h 2 min',
 NULL, NULL, pd03,
 today_6am - INTERVAL'1 day' + INTERVAL'14 h', today_6am - INTERVAL'1 day' + INTERVAL'14 h 5 min'),

-- Rosebank alarm panel — RESOLVED false alarm
(ae07, tenant, rosebank, 'ALARM_PANEL',
 '{"panelId":"ROSEBANK-PANEL-01","zone":2,"event":"TAMPER"}'::jsonb,
 'LOW', 'FALSE_ALARM',
 NULL, -26.1486, 28.0414,
 'Zone 2 tamper alarm. Confirmed as faulty sensor — technician visit booked.',
 g06, d2_6am + INTERVAL'3 h 10 min',
 NULL, NULL, NULL,
 d2_6am + INTERVAL'3 h', d2_6am + INTERVAL'3 h 15 min'),

-- Centurion parking cam motion — RESOLVED, linked to resource custody follow-up
(ae08, tenant, s01, 'CCTV_MOTION',
 '{"cameraId":"HKVN-CT02-2024","motionZone":"ParkingRamp","confidence":0.71}'::jsonb,
 'LOW', 'RESOLVED',
 NULL, -25.8586, 28.1894,
 'Motion on parking ramp at 03:41. Confirmed: authorised cleaning crew.',
 g06, d7_6am + INTERVAL'23 h 45 min',
 NULL, cam02, NULL,
 d7_6am + INTERVAL'23 h 41 min', d7_6am + INTERVAL'23 h 55 min');

-- =============================================================================
-- 9. DISPATCHES FOR NEW EVENTS
-- =============================================================================

INSERT INTO security_dispatches (
    id, tenant_id, alarm_event_id,
    dispatched_unit_type, dispatched_guard_id, dispatched_by,
    dispatched_at, arrived_at, resolved_at,
    outcome, resolution_notes, created_at
) VALUES
-- DURESS response — SAPS + armed response, still open (reinforcing HAWK detail)
(dp03, tenant, ae06,
 'ARMED_RESPONSE', g05, g06,
 today_6am - INTERVAL'1 day' + INTERVAL'14 h 3 min',
 today_6am - INTERVAL'1 day' + INTERVAL'14 h 9 min',  -- 6-minute response
 today_6am - INTERVAL'1 day' + INTERVAL'16 h',
 'RESOLVED',
 'Vehicle drove slowly past property three times — classified as hostile surveillance. SAPS case 220/06/2026 opened. Reinforced guard presence overnight. No entry attempted. Threat actor: unknown silver Polo.',
 today_6am - INTERVAL'1 day' + INTERVAL'14 h 3 min'),

-- Rosebank CCTV response — open, en route
(dp04, tenant, ae05,
 'GUARD', thembisa, g06,
 now_ts - INTERVAL'15 min', NULL, NULL,
 NULL, NULL,
 now_ts - INTERVAL'15 min');

-- =============================================================================
-- 10. PRINCIPAL VETTING RECORDS (Part 9.6)
-- =============================================================================

INSERT INTO security_principal_vetting (
    id, tenant_id, principal_id,
    vetting_type, result,
    conducted_by, conducted_at, next_review_at,
    report_ref, notes,
    created_by, created_at, updated_at
) VALUES
-- HAWK: sanctions check CLEAR
(pv01, tenant, p03,
 'SANCTIONS_SCREENING', 'CLEAR',
 'Refinitiv World-Check', CURRENT_DATE - INTERVAL'35 days',
 CURRENT_DATE + INTERVAL'6 months' - INTERVAL'35 days',
 'WC-2026-ZA-047821', 'No match on OFAC, UN, EU, or SARS watchlists.',
 g06, d30_6am, d30_6am),

-- HAWK: PEP check CLEAR
(pv02, tenant, p03,
 'PEP_CHECK', 'CLEAR',
 'Refinitiv World-Check', CURRENT_DATE - INTERVAL'35 days',
 CURRENT_DATE + INTERVAL'6 months' - INTERVAL'35 days',
 'WC-2026-ZA-047822', 'Not a politically exposed person. No close associates identified as PEPs.',
 g06, d30_6am, d30_6am),

-- OSPREY: sanctions check CLEAR
(pv03, tenant, p04,
 'SANCTIONS_SCREENING', 'CLEAR',
 'Refinitiv World-Check', CURRENT_DATE - INTERVAL'18 days',
 CURRENT_DATE + INTERVAL'6 months' - INTERVAL'18 days',
 'WC-2026-ZA-051103', 'Clean. No watchlist matches.',
 g06, d14_6am, d14_6am),

-- FALCON: adverse media HIT — monitoring ongoing
(pv04, tenant, p02,
 'ADVERSE_MEDIA', 'HIT',
 'Signal Intelligence (Pty) Ltd', CURRENT_DATE - INTERVAL'10 days',
 CURRENT_DATE + INTERVAL'30 days' - INTERVAL'10 days',
 'SIG-2026-AM-00991',
 'Multiple adverse media articles linking principal to controversial municipal budget decisions. No criminal allegations — political in nature. Compliance review initiated. Detail not suspended pending leadership decision.',
 g06, d14_6am, d14_6am),

-- RAVEN (p05): sanctions HIT — engagement declined
(pv05, tenant, p05,
 'SANCTIONS_SCREENING', 'HIT',
 'Refinitiv World-Check', CURRENT_DATE - INTERVAL'65 days',
 NULL,
 'WC-2026-ZA-039041',
 'OFAC SDN list match confirmed. Entity linked to sanctioned entity via ownership chain. Engagement declined.',
 g06, d60_6am, d60_6am),

-- EAGLE: source-of-funds check PENDING
(pv06, tenant, p01,
 'SOURCE_OF_FUNDS', 'PENDING',
 'Werkmans Attorneys — AML Unit',
 NULL, NULL, NULL,
 'Initiated as part of annual review. Expected turnaround 5 business days.',
 g06, d7_6am, d7_6am);

-- Update vetting_status rollup on principals
UPDATE security_principals
SET vetting_status = 'CLEARED', updated_at = now_ts
WHERE id = p03 AND tenant_id = tenant;

UPDATE security_principals
SET vetting_status = 'CLEARED', updated_at = now_ts
WHERE id = p04 AND tenant_id = tenant;

UPDATE security_principals
SET vetting_status = 'FLAGGED', updated_at = now_ts
WHERE id = p02 AND tenant_id = tenant;

UPDATE security_principals
SET vetting_status = 'PENDING', updated_at = now_ts
WHERE id = p01 AND tenant_id = tenant;

UPDATE security_principals
SET vetting_status = 'FLAGGED', active = false, updated_at = now_ts
WHERE id = p05 AND tenant_id = tenant;

-- =============================================================================
-- 11. DECLINED PRINCIPALS REGISTER (Part 9.6)
-- =============================================================================

INSERT INTO security_declined_principals (
    id, tenant_id, principal_id,
    declined_at, declined_by, reason,
    encrypted_detail, created_at
) VALUES
(dec01, tenant, p05,
 CURRENT_DATE - INTERVAL'64 days', g06,
 'Client failed AML/sanctions screening. OFAC SDN list match confirmed via Refinitiv World-Check. Cannot accept engagement under FICA obligations.',
 -- In production this would be AES-256-GCM encrypted by FieldEncryptionService.
 -- Stored as plaintext here for QA only.
 'WC-2026-ZA-039041: OFAC SDN match via shell company VDM Holdings (Pty) Ltd. Ultimate beneficial owner confirmed as sanctioned entity. Legal opinion ref: WA-AML-2026-0341.',
 d60_6am);

-- =============================================================================
-- 12. AUDIT LOG — principal read events (Part 9.3 compliance)
-- =============================================================================

INSERT INTO security_audit_log (
    id, tenant_id, actor_id, actor_type,
    entity_type, entity_id, action,
    old_values, new_values, metadata, occurred_at
) VALUES
-- EAGLE read by Zanele (supervisor viewing medical notes)
(aud01, tenant, g06, 'USER',
 'PRINCIPAL', p01, 'VIEWED',
 NULL, NULL,
 '{"codename":"EAGLE","context":"single_view"}'::jsonb,
 d7_6am + INTERVAL'9 h'),

-- EAGLE read again — same supervisor
(aud02, tenant, g06, 'USER',
 'PRINCIPAL', p01, 'VIEWED',
 NULL, NULL,
 '{"codename":"EAGLE","context":"single_view"}'::jsonb,
 d2_6am + INTERVAL'8 h 30 min'),

-- HAWK read by supervisor during vetting process
(aud03, tenant, g06, 'USER',
 'PRINCIPAL', p03, 'VIEWED',
 NULL, NULL,
 '{"codename":"HAWK","context":"list_view"}'::jsonb,
 d30_6am + INTERVAL'10 h'),

-- RAVEN read before declination decision
(aud04, tenant, g06, 'USER',
 'PRINCIPAL', p05, 'VIEWED',
 NULL, NULL,
 '{"codename":"RAVEN","context":"single_view"}'::jsonb,
 d60_6am + INTERVAL'11 h'),

-- Principal status changed — RAVEN deactivated on declination
('c3200000-0000-0000-0000-000000000005'::uuid, tenant, g06, 'USER',
 'PRINCIPAL', p05, 'STATUS_CHANGED',
 '{"active":true,"vettingStatus":"FLAGGED"}'::jsonb,
 '{"active":false,"reason":"Declined — sanctions hit"}'::jsonb,
 '{"codename":"RAVEN"}'::jsonb,
 d60_6am + INTERVAL'11 h 30 min'),

-- FALCON adverse media HIT — compliance action logged
('c3200000-0000-0000-0000-000000000006'::uuid, tenant, g06, 'USER',
 'PRINCIPAL', p02, 'UPDATED',
 '{"vettingStatus":"UNVETTED"}'::jsonb,
 '{"vettingStatus":"FLAGGED","trigger":"adverse_media_hit"}'::jsonb,
 '{"codename":"FALCON","vettingRef":"SIG-2026-AM-00991"}'::jsonb,
 d14_6am + INTERVAL'16 h');

-- =============================================================================
-- 13. HISTORICAL SHIFTS (90 days) — drives meaningful report data
--
-- Strategy: Centurion (s01) — 95% completion rate (best performer)
--           Germiston (s02) — 78% completion rate (occasional misses)
--           Sandton — 88% completion rate (one MISSED stretch during cam outage)
--
-- Using DO sub-blocks with LOOP to generate batches efficiently.
-- =============================================================================

-- Generate 30 days of Centurion 12h day/night shifts (mostly COMPLETED, some MISSED)
-- Day shifts: 06:00-18:00  |  Night shifts: 18:00-06:00
-- Guards rotate: priya, thembisa, g01, g02

DO $hist$
DECLARE
    tenant  UUID := '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f';
    s01     UUID := 'b2000000-0000-0000-0000-000000000001';
    s02     UUID := 'b2000000-0000-0000-0000-000000000002';
    sandton UUID := 'acdce7e8-2557-4dd7-bd9b-70c3cd62e51f';
    priya   UUID := '0708db71-bf26-4022-9930-464713fec05a';
    thembisa UUID := '9573cec7-7207-4cdc-b9dc-8f747fefa52c';
    g01     UUID := 'b1000000-0000-0000-0000-000000000001';
    g02     UUID := 'b1000000-0000-0000-0000-000000000002';
    g05     UUID := 'b1000000-0000-0000-0000-000000000005';
    g06     UUID := 'b1000000-0000-0000-0000-000000000006';

    d INTEGER;
    shift_start TIMESTAMPTZ;
    day_guard UUID;
    night_guard UUID;
    site_id UUID;
    day_status TEXT;
    night_status TEXT;
    guards UUID[] := ARRAY[priya, thembisa, g01, g02, g05, g06];
BEGIN
    FOR d IN 1..90 LOOP
        shift_start := CURRENT_DATE::TIMESTAMPTZ - (d || ' days')::INTERVAL + INTERVAL'6 hours';

        -- Rotate guards round-robin
        day_guard   := guards[ 1 + ((d * 2)     % array_length(guards, 1)) ];
        night_guard := guards[ 1 + ((d * 2 + 1) % array_length(guards, 1)) ];

        -- Centurion: 95% completion — miss every ~20th shift
        day_status   := CASE WHEN d % 20 = 0 THEN 'MISSED' ELSE 'COMPLETED' END;
        night_status := CASE WHEN d % 22 = 0 THEN 'MISSED' ELSE 'COMPLETED' END;

        INSERT INTO security_shifts (
            id, tenant_id, site_id, guard_id,
            start_at, end_at, status, notes,
            created_at, updated_at, deleted_at
        ) VALUES
        (gen_random_uuid(), tenant, s01, day_guard,
         shift_start, shift_start + INTERVAL'12 hours',
         day_status, NULL,
         shift_start - INTERVAL'2 days', shift_start + INTERVAL'12 hours', NULL),
        (gen_random_uuid(), tenant, s01, night_guard,
         shift_start + INTERVAL'12 hours', shift_start + INTERVAL'24 hours',
         night_status, NULL,
         shift_start - INTERVAL'2 days', shift_start + INTERVAL'24 hours', NULL);

        -- Germiston: 78% — miss every ~5th shift (more problematic site)
        day_status := CASE WHEN d % 5 = 0 THEN 'MISSED' ELSE 'COMPLETED' END;

        INSERT INTO security_shifts (
            id, tenant_id, site_id, guard_id,
            start_at, end_at, status, notes,
            created_at, updated_at, deleted_at
        ) VALUES
        (gen_random_uuid(), tenant, s02, night_guard,
         shift_start, shift_start + INTERVAL'12 hours',
         day_status, NULL,
         shift_start - INTERVAL'2 days', shift_start + INTERVAL'12 hours', NULL);

        -- Sandton: 88% — miss during cam outage window (days 9-14) and random
        day_status := CASE
            WHEN d BETWEEN 9 AND 14 THEN 'MISSED'
            WHEN d % 15 = 0 THEN 'MISSED'
            ELSE 'COMPLETED'
        END;

        INSERT INTO security_shifts (
            id, tenant_id, site_id, guard_id,
            start_at, end_at, status, notes,
            created_at, updated_at, deleted_at
        ) VALUES
        (gen_random_uuid(), tenant, sandton, day_guard,
         shift_start, shift_start + INTERVAL'12 hours',
         day_status, NULL,
         shift_start - INTERVAL'2 days', shift_start + INTERVAL'12 hours', NULL);

    END LOOP;
END $hist$;

-- =============================================================================
-- 14. HISTORICAL INCIDENTS (spread across 90 days for report heat maps)
-- =============================================================================

INSERT INTO security_incidents (
    id, tenant_id, site_id, shift_id, guard_id,
    type, severity, description,
    occurred_at, resolved_at,
    created_at, updated_at
)
SELECT
    gen_random_uuid(),
    '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f'::uuid,
    site_id_txt::uuid,
    NULL,
    guard_id_txt::uuid,
    inc_type, severity, description,
    created,                           -- occurred_at
    created + INTERVAL'5 hours',       -- resolved_at
    created, created + INTERVAL'5 hours'
FROM (VALUES
    ('b2000000-0000-0000-0000-000000000001'::uuid, '0708db71-bf26-4022-9930-464713fec05a'::uuid, 'THEFT',     'HIGH',     'Theft of materials from loading bay',   NOW() - INTERVAL'5 days'),
    ('b2000000-0000-0000-0000-000000000002'::uuid, '9573cec7-7207-4cdc-b9dc-8f747fefa52c'::uuid, 'TRESPASS',  'CRITICAL', 'Perimeter fence cut - Germiston East',  NOW() - INTERVAL'8 days'),
    ('acdce7e8-2557-4dd7-bd9b-70c3cd62e51f'::uuid, '6039c07e-b622-431d-9494-4e692c351602'::uuid, 'THEFT',     'LOW',      'Shoplifter apprehended - Level 1',      NOW() - INTERVAL'11 days'),
    ('b2000000-0000-0000-0000-000000000001'::uuid, 'f96bb986-aa5c-49fa-96fa-7c8a4b94da57'::uuid, 'THEFT',     'MEDIUM',   'Vehicle break-in at parking P2',        NOW() - INTERVAL'15 days'),
    ('acdce7e8-2557-4dd7-bd9b-70c3cd62e51f'::uuid, '0708db71-bf26-4022-9930-464713fec05a'::uuid, 'FIRE',      'HIGH',     'Fire alarm - kitchen malfunction',      NOW() - INTERVAL'18 days'),
    ('b2000000-0000-0000-0000-000000000002'::uuid, '9573cec7-7207-4cdc-b9dc-8f747fefa52c'::uuid, 'ASSAULT',   'CRITICAL', 'Assault on guard during protest',       NOW() - INTERVAL'22 days'),
    ('b2000000-0000-0000-0000-000000000001'::uuid, '6039c07e-b622-431d-9494-4e692c351602'::uuid, 'TRESPASS',  'MEDIUM',   'Tailgating at staff entrance',          NOW() - INTERVAL'28 days'),
    ('acdce7e8-2557-4dd7-bd9b-70c3cd62e51f'::uuid, 'f96bb986-aa5c-49fa-96fa-7c8a4b94da57'::uuid, 'OTHER',     'HIGH',     'Guard equipment tampered with',         NOW() - INTERVAL'33 days'),
    ('b2000000-0000-0000-0000-000000000002'::uuid, '0708db71-bf26-4022-9930-464713fec05a'::uuid, 'SUSPICIOUS','HIGH',     'Unauthorised drone over yard',          NOW() - INTERVAL'41 days'),
    ('b2000000-0000-0000-0000-000000000001'::uuid, '9573cec7-7207-4cdc-b9dc-8f747fefa52c'::uuid, 'THEFT',     'MEDIUM',   'Attempted card cloning at ATM',         NOW() - INTERVAL'50 days'),
    ('acdce7e8-2557-4dd7-bd9b-70c3cd62e51f'::uuid, '6039c07e-b622-431d-9494-4e692c351602'::uuid, 'OTHER',     'CRITICAL', 'Bomb threat - mall evacuated',          NOW() - INTERVAL'55 days'),
    ('b2000000-0000-0000-0000-000000000002'::uuid, 'f96bb986-aa5c-49fa-96fa-7c8a4b94da57'::uuid, 'MEDICAL',   'HIGH',     'Forklift accident - 1 injured',         NOW() - INTERVAL'63 days'),
    ('b2000000-0000-0000-0000-000000000001'::uuid, '0708db71-bf26-4022-9930-464713fec05a'::uuid, 'ASSAULT',   'CRITICAL', 'Armed robbery foiled by patrol',        NOW() - INTERVAL'71 days'),
    ('acdce7e8-2557-4dd7-bd9b-70c3cd62e51f'::uuid, '9573cec7-7207-4cdc-b9dc-8f747fefa52c'::uuid, 'VANDALISM', 'LOW',      'Graffiti on external wall',             NOW() - INTERVAL'77 days'),
    ('b2000000-0000-0000-0000-000000000002'::uuid, '6039c07e-b622-431d-9494-4e692c351602'::uuid, 'THEFT',     'HIGH',     'Equipment theft from cage store',       NOW() - INTERVAL'84 days')
) AS t(site_id_txt, guard_id_txt, inc_type, severity, description, created)
WHERE NOT EXISTS (
    SELECT 1 FROM security_incidents si
    WHERE si.description = t.description
      AND si.tenant_id = '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f'
);

END $$;

COMMIT;