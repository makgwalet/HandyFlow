-- =============================================================================
-- HandyFlow Security module: DEMO DATA
--
-- Fills one tenant with a believable, clearly fake security company so every Security screen has
-- something to show: sites, guards in every compliance state, this week's roster (with deliberate
-- conflicts), live guards, patrol rounds in every status, incidents, complaints, a gate register,
-- screening, competencies, ratings, devices, armoury, payroll and report history.
--
-- THIS IS NOT A FLYWAY MIGRATION. It lives outside db/migration on purpose and never runs by itself.
-- Use it on a development, demo or test tenant only. Do not run it on a tenant with real operations.
--
-- Everything is fake: names, ID numbers (DEMO-...), PSiRA numbers (DEMO-...), phone numbers (082 000 00xx)
-- and bank details. Every row it creates has an id beginning d3d3d3d3-, which is how
-- security-demo-data-remove.sql finds and deletes exactly these rows and nothing else.
--
-- Times are relative to when you run it ("on duty now", "last Tuesday"), so run it the day you want to demo.
--
-- Run (the tenant must already exist):
--   psql -h localhost -U handyflow -d handyflow -v tenant=<TENANT-UUID> -f security-demo-data.sql
-- Docker (PowerShell):
--   Get-Content security-demo-data.sql | docker exec -i handyflow-db psql -U handyflow -d handyflow -v tenant=<TENANT-UUID>
-- Remove again:
--   psql ... -v tenant=<TENANT-UUID> -f security-demo-data-remove.sql
-- =============================================================================
\set ON_ERROR_STOP on
\if :{?tenant}
\else
  \echo 'Usage: psql ... -v tenant=<TENANT-UUID> -f security-demo-data.sql'
  \quit
\endif

BEGIN;
SELECT set_config('hf.demo_tenant', :'tenant', true) AS _tenant \gset

-- Deterministic ids: d3d3d3d3-<kind>-4000-8000-<number>. Kind numbers are listed in the guide.
CREATE FUNCTION pg_temp.did(kind int, n int) RETURNS uuid LANGUAGE sql IMMUTABLE AS
$f$ SELECT format('d3d3d3d3-%s-4000-8000-%s', lpad(kind::text, 4, '0'), lpad(n::text, 12, '0'))::uuid $f$;
CREATE FUNCTION pg_temp.t() RETURNS uuid LANGUAGE sql STABLE AS
$f$ SELECT current_setting('hf.demo_tenant')::uuid $f$;
-- "Now" and day starts in UTC for timestamp-without-time-zone columns, plus the South African (UTC+2) week.
CREATE FUNCTION pg_temp.utc_now() RETURNS timestamp LANGUAGE sql STABLE AS $f$ SELECT now() AT TIME ZONE 'UTC' $f$;
-- Monday 00:00 South African time of the current week, as a UTC instant.
CREATE FUNCTION pg_temp.week_start() RETURNS timestamptz LANGUAGE sql STABLE AS
$f$ SELECT date_trunc('week', now() AT TIME ZONE 'Africa/Johannesburg') AT TIME ZONE 'Africa/Johannesburg' $f$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM tenants WHERE id = pg_temp.t()) THEN
    RAISE EXCEPTION 'Tenant % does not exist. Pass an existing tenant id with -v tenant=<uuid>.', pg_temp.t();
  END IF;
  IF EXISTS (SELECT 1 FROM security_guards WHERE id::text LIKE 'd3d3d3d3-%') THEN
    RAISE EXCEPTION 'Demo data is already loaded. Run security-demo-data-remove.sql first.';
  END IF;
END $$;

-- ── Branches ───────────────────────────────────────────────────────────────────────────────────
INSERT INTO security_branches (id, tenant_id, name, region, description, active, created_at, updated_at) VALUES
 (pg_temp.did(1,1), pg_temp.t(), 'DEMO Gauteng North', 'Gauteng', 'Pretoria, Centurion and Midrand', true, now(), now()),
 (pg_temp.did(1,2), pg_temp.t(), 'DEMO Gauteng South', 'Gauteng', 'Johannesburg, Sandton and the East Rand', true, now(), now());

-- ── Sites ──────────────────────────────────────────────────────────────────────────────────────
-- Contract states: running, expiring soon, long running, terminated. One site has no map position.
INSERT INTO security_sites (id, tenant_id, name, address, latitude, longitude, contact_name, contact_phone, instructions,
                            qr_secret, active, contract_status, contract_start, contract_end, termination_reason, terminated_at,
                            portal_enabled, portal_label, branch_id, require_signed_qr, created_at, updated_at) VALUES
 (pg_temp.did(2,1), pg_temp.t(), 'DEMO Centurion Mall',
  '{"street":"Heuwel Road","suburb":"Centurion","city":"Pretoria","province":"Gauteng","postalCode":"0157"}',
  -25.8603, 28.1894, 'Mr A. Naidoo (centre manager)', '082 000 0001',
  'Main entrance staffed 06:00-22:00. Report loading-bay deliveries after 18:00. Do not leave the CCTV room unattended.',
  md5('demo-site-1'), true, 'ACTIVE', current_date - 400, current_date + 200, NULL, NULL, true, 'Centurion Mall', pg_temp.did(1,1), false, now() - interval '400 days', now()),
 (pg_temp.did(2,2), pg_temp.t(), 'DEMO Sandton Business Park',
  '{"street":"Rivonia Road","suburb":"Sandown","city":"Johannesburg","province":"Gauteng","postalCode":"2196"}',
  -26.1076, 28.0567, 'Ms T. Pillay', '082 000 0002',
  'Armed response patrol on the hour. Visitors must sign in at the boom gate and leave an ID copy.',
  md5('demo-site-2'), true, 'EXPIRING_SOON', current_date - 700, current_date + 25, NULL, NULL, false, NULL, pg_temp.did(1,2), true, now() - interval '700 days', now()),
 (pg_temp.did(2,3), pg_temp.t(), 'DEMO Midrand Logistics Hub',
  '{"street":"New Road","suburb":"Halfway House","city":"Midrand","province":"Gauteng","postalCode":"1685"}',
  -25.9969, 28.1288, 'Mr P. Joubert', '082 000 0003',
  'Truck gate open 24 hours. Log every trailer in and out. Seal numbers must match the waybill.',
  md5('demo-site-3'), true, 'ACTIVE', current_date - 120, current_date + 610, NULL, NULL, false, NULL, pg_temp.did(1,1), false, now() - interval '120 days', now()),
 (pg_temp.did(2,4), pg_temp.t(), 'DEMO Pretoria CBD Offices',
  '{"street":"Church Street","suburb":"Pretoria Central","city":"Pretoria","province":"Gauteng","postalCode":"0002"}',
  -25.7461, 28.1881, 'Ms L. Venter', '082 000 0004',
  'Reception and basement parking. No visitor access to floors 9 to 12 after 17:00.',
  md5('demo-site-4'), true, 'ACTIVE', current_date - 90, current_date + 275, NULL, NULL, false, NULL, pg_temp.did(1,1), false, now() - interval '90 days', now()),
 (pg_temp.did(2,5), pg_temp.t(), 'DEMO Rosebank Tower',
  '{"street":"Cradock Avenue","suburb":"Rosebank","city":"Johannesburg","province":"Gauteng","postalCode":"2196"}',
  -26.1450, 28.0407, 'Mr S. Moyo', '082 000 0005',
  'Lift lobby access control. Challenge anyone without a tag.',
  md5('demo-site-5'), true, 'ACTIVE', current_date - 30, current_date + 335, NULL, NULL, false, NULL, pg_temp.did(1,2), false, now() - interval '30 days', now()),
 (pg_temp.did(2,6), pg_temp.t(), 'DEMO Boksburg Warehouse',
  '{"street":"North Rand Road","suburb":"Anderbolt","city":"Boksburg","province":"Gauteng","postalCode":"1459"}',
  -26.2120, 28.2590, 'Mr D. Botha', '082 000 0006',
  'Contract ended. Guards withdrawn.',
  md5('demo-site-6'), false, 'TERMINATED', current_date - 500, current_date - 20, 'Client moved to an in-house guarding team.', now() - interval '20 days', false, NULL, pg_temp.did(1,2), false, now() - interval '500 days', now()),
 (pg_temp.did(2,7), pg_temp.t(), 'DEMO Fourways Estate (no map position)',
  '{"street":"William Nicol Drive","suburb":"Fourways","city":"Johannesburg","province":"Gauteng"}',
  NULL, NULL, 'Ms R. Govender', '082 000 0007',
  'Newly signed site. Position not captured yet, so it shows as "no position" on the map.',
  md5('demo-site-7'), true, 'ACTIVE', current_date - 5, current_date + 360, NULL, NULL, false, NULL, pg_temp.did(1,2), false, now() - interval '5 days', now());

INSERT INTO security_contacts (id, tenant_id, site_id, name, role, phone, email, active, created_at, updated_at) VALUES
 (pg_temp.did(37,1), pg_temp.t(), pg_temp.did(2,1), 'DEMO SAPS Centurion',        'POLICE',           '082 000 0101', NULL,                    true, now(), now()),
 (pg_temp.did(37,2), pg_temp.t(), pg_temp.did(2,1), 'DEMO Centre Manager',        'SITE_MANAGER',     '082 000 0001', 'manager@demo.invalid',  true, now(), now()),
 (pg_temp.did(37,3), pg_temp.t(), pg_temp.did(2,2), 'DEMO Park Facilities',       'CLIENT_CONTACT',   '082 000 0002', 'facilities@demo.invalid', true, now(), now()),
 (pg_temp.did(37,4), pg_temp.t(), NULL,             'DEMO Control Room',          'CONTROL_ROOM',     '082 000 0100', 'control@demo.invalid',  true, now(), now()),
 (pg_temp.did(37,5), pg_temp.t(), NULL,             'DEMO Ambulance',             'AMBULANCE',        '082 000 0102', NULL,                    true, now(), now()),
 (pg_temp.did(37,6), pg_temp.t(), NULL,             'DEMO Fire Department',       'FIRE',             '082 000 0103', NULL,                    true, now(), now());

-- ── Posts ──────────────────────────────────────────────────────────────────────────────────────
INSERT INTO security_posts (id, tenant_id, site_id, name, description, active, created_at, updated_at) VALUES
 (pg_temp.did(25,1), pg_temp.t(), pg_temp.did(2,1), 'Main entrance', 'Reception desk and bag checks', true, now(), now()),
 (pg_temp.did(25,2), pg_temp.t(), pg_temp.did(2,1), 'Loading bay', 'Deliveries and waste', true, now(), now()),
 (pg_temp.did(25,3), pg_temp.t(), pg_temp.did(2,2), 'Boom gate', 'Visitor sign-in and vehicle checks', true, now(), now()),
 (pg_temp.did(25,4), pg_temp.t(), pg_temp.did(2,3), 'Truck gate', 'Trailer log and seal checks', true, now(), now());

-- ── Guards ─────────────────────────────────────────────────────────────────────────────────────
-- One guard per situation the screens must handle. Names are fictional.
INSERT INTO security_guards (id, tenant_id, first_name, last_name, psira_number, id_number, phone, grade, active, notes,
                             status, status_note, status_changed_at, psira_expiry_date, screening_status,
                             firearm_competency_number, firearm_competency_expiry, cp_vetting_tier, cp_vetting_cleared_at, cp_vetting_expires_at,
                             hourly_rate_cents, rate_effective_from, primary_branch_id, employee_code,
                             emergency_contact_name, emergency_contact_phone, bank_name, bank_account_number, bank_branch_code,
                             created_at, updated_at) VALUES
 (pg_temp.did(3,1),  pg_temp.t(), 'Thabo',     'Mokoena',   'DEMO-PSR-0001', 'DEMO-ID-0001', '082 000 0011', 'A', true, 'Shift supervisor. Reliable, 6 years.',
  'ACTIVE', NULL, NULL, current_date + 400, 'CLEARED', 'DEMO-FA-0001', current_date + 300, NULL, NULL, NULL, 9500, current_date - 200, pg_temp.did(1,1), 'DEMO-G01', 'Mpho Mokoena', '082 000 0511', 'DEMO Bank', '000000000001', '000000', now() - interval '900 days', now()),
 (pg_temp.did(3,2),  pg_temp.t(), 'Lerato',    'Dlamini',   'DEMO-PSR-0002', 'DEMO-ID-0002', '082 000 0012', 'B', true, NULL,
  'ACTIVE', NULL, NULL, current_date + 200, 'CLEARED', NULL, NULL, NULL, NULL, NULL, 8200, current_date - 200, pg_temp.did(1,1), 'DEMO-G02', 'Sibusiso Dlamini', '082 000 0512', 'DEMO Bank', '000000000002', '000000', now() - interval '700 days', now()),
 (pg_temp.did(3,3),  pg_temp.t(), 'Sipho',     'Ndlovu',    'DEMO-PSR-0003', 'DEMO-ID-0003', '082 000 0013', 'C', true, 'Armed response qualified.',
  'ACTIVE', NULL, NULL, current_date + 90, 'CLEARED', 'DEMO-FA-0003', current_date + 45, NULL, NULL, NULL, 7200, current_date - 200, pg_temp.did(1,2), 'DEMO-G03', 'Nandi Ndlovu', '082 000 0513', 'DEMO Bank', '000000000003', '000000', now() - interval '500 days', now()),
 (pg_temp.did(3,4),  pg_temp.t(), 'Naledi',    'Khumalo',   'DEMO-PSR-0004', 'DEMO-ID-0004', '082 000 0014', 'C', true, 'PSiRA registration expires soon.',
  'ACTIVE', NULL, NULL, current_date + 25, 'CLEARED', NULL, NULL, NULL, NULL, NULL, 7200, current_date - 200, pg_temp.did(1,1), 'DEMO-G04', 'Themba Khumalo', '082 000 0514', 'DEMO Bank', '000000000004', '000000', now() - interval '400 days', now()),
 (pg_temp.did(3,5),  pg_temp.t(), 'Pieter',    'van Wyk',   'DEMO-PSR-0005', 'DEMO-ID-0005', '082 000 0015', 'C', true, 'PSiRA expires in 15 days; screening renewal pending.',
  'ACTIVE', NULL, NULL, current_date + 15, 'PENDING', NULL, NULL, NULL, NULL, NULL, 7200, current_date - 200, pg_temp.did(1,2), 'DEMO-G05', 'Anna van Wyk', '082 000 0515', 'DEMO Bank', '000000000005', '000000', now() - interval '350 days', now()),
 (pg_temp.did(3,6),  pg_temp.t(), 'Zanele',    'Mthembu',   'DEMO-PSR-0006', 'DEMO-ID-0006', '082 000 0016', 'D', true, 'PSiRA expired 10 days ago. Cannot be scheduled until renewed.',
  'ACTIVE', NULL, NULL, current_date - 10, 'CLEARED', NULL, NULL, NULL, NULL, NULL, 6400, current_date - 200, pg_temp.did(1,2), 'DEMO-G06', 'Zodwa Mthembu', '082 000 0516', 'DEMO Bank', '000000000006', '000000', now() - interval '600 days', now()),
 (pg_temp.did(3,7),  pg_temp.t(), 'Johan',     'Pretorius', 'DEMO-PSR-0007', 'DEMO-ID-0007', '082 000 0017', 'B', true, 'Close protection (enhanced vetting).',
  'ACTIVE', NULL, NULL, current_date + 300, 'CLEARED', 'DEMO-FA-0007', current_date + 150, 'ENHANCED', current_date - 60, current_date + 305, 8800, current_date - 200, pg_temp.did(1,1), 'DEMO-G07', 'Elna Pretorius', '082 000 0517', 'DEMO Bank', '000000000007', '000000', now() - interval '800 days', now()),
 (pg_temp.did(3,8),  pg_temp.t(), 'Nomsa',     'Sithole',   'DEMO-PSR-0008', 'DEMO-ID-0008', '082 000 0018', 'D', true, 'Failed a drug test; screening flagged.',
  'ACTIVE', NULL, NULL, current_date + 150, 'FLAGGED', NULL, NULL, NULL, NULL, NULL, 6400, current_date - 200, pg_temp.did(1,2), 'DEMO-G08', 'Lucky Sithole', '082 000 0518', 'DEMO Bank', '000000000008', '000000', now() - interval '250 days', now()),
 (pg_temp.did(3,9),  pg_temp.t(), 'Kagiso',    'Molefe',    'DEMO-PSR-0009', 'DEMO-ID-0009', '082 000 0019', 'E', true, 'New hire. Screening not started.',
  'ACTIVE', NULL, NULL, current_date + 500, 'UNSCREENED', NULL, NULL, NULL, NULL, NULL, 5600, current_date - 14, pg_temp.did(1,1), 'DEMO-G09', 'Boitumelo Molefe', '082 000 0519', 'DEMO Bank', '000000000009', '000000', now() - interval '14 days', now()),
 (pg_temp.did(3,10), pg_temp.t(), 'Ayanda',    'Zulu',      'DEMO-PSR-0010', 'DEMO-ID-0010', '082 000 0020', 'C', true, NULL,
  'ACTIVE', NULL, NULL, current_date + 60, 'CLEARED', NULL, NULL, NULL, NULL, NULL, 7200, current_date - 200, pg_temp.did(1,1), 'DEMO-G10', 'Sne Zulu', '082 000 0520', 'DEMO Bank', '000000000010', '000000', now() - interval '300 days', now()),
 (pg_temp.did(3,11), pg_temp.t(), 'Mandla',    'Nkosi',     'DEMO-PSR-0011', 'DEMO-ID-0011', '082 000 0021', 'C', true, NULL,
  'SUSPENDED', 'Suspended pending disciplinary hearing (see complaint CMP-DEMO-05).', now() - interval '6 days', current_date + 120, 'CLEARED', NULL, NULL, NULL, NULL, NULL, 7200, current_date - 200, pg_temp.did(1,2), 'DEMO-G11', 'Rose Nkosi', '082 000 0521', 'DEMO Bank', '000000000011', '000000', now() - interval '450 days', now()),
 (pg_temp.did(3,12), pg_temp.t(), 'Refilwe',   'Maseko',    'DEMO-PSR-0012', 'DEMO-ID-0012', '082 000 0022', 'D', true, NULL,
  'ON_LEAVE', 'Annual leave until next week.', now() - interval '3 days', current_date + 220, 'CLEARED', NULL, NULL, NULL, NULL, NULL, 6400, current_date - 200, pg_temp.did(1,1), 'DEMO-G12', 'Tumi Maseko', '082 000 0522', 'DEMO Bank', '000000000012', '000000', now() - interval '380 days', now()),
 (pg_temp.did(3,13), pg_temp.t(), 'Gerhard',   'Botha',     'DEMO-PSR-0013', 'DEMO-ID-0013', '082 000 0023', 'B', true, 'Close protection team leader (high vetting).',
  'ACTIVE', NULL, NULL, current_date + 365, 'CLEARED', 'DEMO-FA-0013', current_date + 200, 'HIGH', current_date - 90, current_date + 275, 8800, current_date - 200, pg_temp.did(1,2), 'DEMO-G13', 'Marie Botha', '082 000 0523', 'DEMO Bank', '000000000013', '000000', now() - interval '1000 days', now()),
 (pg_temp.did(3,14), pg_temp.t(), 'Busisiwe',  'Cele',      'DEMO-PSR-0014', 'DEMO-ID-0014', '082 000 0024', 'E', false, 'Resigned.',
  'TERMINATED', 'Resigned to study full time.', now() - interval '40 days', current_date + 30, 'CLEARED', NULL, NULL, NULL, NULL, NULL, 5600, current_date - 200, pg_temp.did(1,1), 'DEMO-G14', 'Sizwe Cele', '082 000 0524', 'DEMO Bank', '000000000014', '000000', now() - interval '500 days', now());

INSERT INTO security_branch_assignments (id, tenant_id, branch_id, entity_type, entity_id, role, assigned_at, active)
SELECT pg_temp.did(50, row_number() OVER ()::int), pg_temp.t(), g.primary_branch_id, 'GUARD', g.id, 'GUARD', now(), true
FROM security_guards g WHERE g.id::text LIKE 'd3d3d3d3-%' AND g.active;

-- ── Pay grades ─────────────────────────────────────────────────────────────────────────────────
INSERT INTO security_grade_rates (id, tenant_id, grade, hourly_rate_cents, standard_hours_per_day, effective_from, created_at) VALUES
 (pg_temp.did(38,1), pg_temp.t(), 'A', 9500, 8, current_date - 200, now()),
 (pg_temp.did(38,2), pg_temp.t(), 'B', 8200, 8, current_date - 200, now()),
 (pg_temp.did(38,3), pg_temp.t(), 'C', 7200, 8, current_date - 200, now()),
 (pg_temp.did(38,4), pg_temp.t(), 'D', 6400, 8, current_date - 200, now()),
 (pg_temp.did(38,5), pg_temp.t(), 'E', 5600, 8, current_date - 200, now());

-- ── Shifts ─────────────────────────────────────────────────────────────────────────────────────
-- A rota two weeks back to the end of next week. Slots are 8 hours (06-14, 14-22, 22-06), Monday to Sunday,
-- with one guard per slot per day, so the base rota has no conflicts. Status follows the clock.
-- (day offset 0 = Monday of the current week, South African time)
CREATE TEMP TABLE _slot (site int, hour int, mins int, dow_guards int[]);
INSERT INTO _slot VALUES
 (1,  6, 4, ARRAY[2,2,2,2,2,10,10]),
 (1, 14, 4, ARRAY[4,4,4,4,4,NULL,NULL]),
 (1, 22, 4, ARRAY[8,8,8,8,8,NULL,NULL]),
 (2,  6, 3, ARRAY[3,3,3,3,3,13,NULL]),
 (2, 22, 3, ARRAY[13,13,13,13,NULL,NULL,NULL]),
 (3,  6, 2, ARRAY[5,5,5,5,5,NULL,NULL]),
 (4,  6, 2, ARRAY[1,1,1,1,1,NULL,NULL]),
 (5,  6, 2, ARRAY[7,7,7,7,7,NULL,NULL]);

CREATE TEMP TABLE _roster AS
SELECT row_number() OVER (ORDER BY d.day_off, sl.site, sl.hour)::int AS n,
       sl.site, d.day_off, sl.hour, sl.mins,
       sl.dow_guards[((d.day_off % 7) + 7) % 7 + 1] AS guard,
       (pg_temp.week_start() + d.day_off * interval '24 hours' + sl.hour * interval '1 hour') AS start_ts
FROM _slot sl CROSS JOIN generate_series(-14, 13) AS d(day_off)
WHERE sl.dow_guards[((d.day_off % 7) + 7) % 7 + 1] IS NOT NULL;

INSERT INTO security_shifts (id, tenant_id, site_id, guard_id, start_at, end_at, status, notes, min_scan_count, created_at, updated_at)
SELECT pg_temp.did(7, r.n), pg_temp.t(), pg_temp.did(2, r.site), pg_temp.did(3, r.guard),
       r.start_ts AT TIME ZONE 'UTC', (r.start_ts + interval '8 hours') AT TIME ZONE 'UTC',
       CASE WHEN r.start_ts + interval '8 hours' < now() THEN 'COMPLETED'
            WHEN r.start_ts <= now() THEN 'ACTIVE' ELSE 'SCHEDULED' END,
       CASE WHEN r.site = 2 AND r.hour = 22 THEN 'Armed patrol. Check Gate A every hour.' ELSE NULL END,
       r.mins, (r.start_ts - interval '7 days') AT TIME ZONE 'UTC', pg_temp.utc_now()
FROM _roster r;

-- Deliberate conflicts in the current week (the scheduler flags these; the server would have refused most of them
-- if created through the app, which is why they are seeded directly).
INSERT INTO security_shifts (id, tenant_id, site_id, guard_id, start_at, end_at, status, notes, min_scan_count, created_at, updated_at)
SELECT pg_temp.did(7, c.n), pg_temp.t(), pg_temp.did(2, c.site), pg_temp.did(3, c.guard),
       s AT TIME ZONE 'UTC', (s + interval '8 hours') AT TIME ZONE 'UTC',
       CASE WHEN s + interval '8 hours' < now() THEN 'COMPLETED' WHEN s <= now() THEN 'ACTIVE' ELSE 'SCHEDULED' END,
       c.note, 2, pg_temp.utc_now() - interval '2 days', pg_temp.utc_now()
FROM (VALUES
  (901, 4, 4, 3, 10, 'DEMO conflict: overlaps this guard''s 14:00 shift at Centurion Mall (red).'),
  (902, 5, 3, 2, 20, 'DEMO conflict: starts 6 hours after this guard''s previous shift ended (amber, under 8 hours rest).'),
  (903, 4, 1, 5,  6, 'DEMO conflict: with Sunday this takes the guard over 45 hours this week (flag).'),
  (904, 4, 1, 6,  6, 'DEMO conflict: second weekend shift; weekly hours over 45 (flag).'),
  (905, 3, 6, 4, 14, 'DEMO conflict: this guard''s PSiRA registration expired before this shift (red).'),
  (906, 5, 11, 3,  6, 'DEMO conflict: guard is suspended (amber, not active).')
) AS c(n, site, guard, day_off, hour, note),
LATERAL (SELECT pg_temp.week_start() + CASE WHEN c.n IN (903) THEN 5 WHEN c.n = 904 THEN 6 ELSE c.day_off END * interval '24 hours' + c.hour * interval '1 hour' AS s) AS x;

-- Extra cover starting around "now" so Live Operations always has people on duty, whatever time you run this.
-- Each is added only when the guard has no other shift at that time.
INSERT INTO security_shifts (id, tenant_id, site_id, guard_id, start_at, end_at, status, notes, min_scan_count, overtime_alert_sent_at, created_at, updated_at)
SELECT pg_temp.did(7, a.n), pg_temp.t(), pg_temp.did(2, a.site), pg_temp.did(3, a.guard),
       (now() + a.from_h * interval '1 hour') AT TIME ZONE 'UTC', (now() + (a.from_h + a.len) * interval '1 hour') AT TIME ZONE 'UTC',
       'ACTIVE', a.note, 2,
       CASE WHEN a.from_h + a.len < 0 THEN (now() + (a.from_h + a.len) * interval '1 hour' + interval '10 minutes') END,
       (now() - interval '2 days') AT TIME ZONE 'UTC', pg_temp.utc_now()
FROM (VALUES
  (950, 4, 10, -2, 8, 'DEMO extra cover.'),
  (951, 5, 2,  -3, 8, 'DEMO extra cover.'),
  (952, 3, 3,  -1, 8, 'DEMO extra cover.'),
  (953, 1, 13, -5, 8, 'DEMO extra cover.'),
  (954, 3, 9, -14, 12, 'DEMO overrun: still on site after the shift should have ended (overtime alert sent).')
) AS a(n, site, guard, from_h, len, note)
WHERE NOT EXISTS (
  SELECT 1 FROM security_shifts o
  WHERE o.guard_id = pg_temp.did(3, a.guard) AND o.status IN ('ACTIVE', 'SCHEDULED', 'COMPLETED')
    AND o.start_at < ((now() + (a.from_h + a.len) * interval '1 hour') AT TIME ZONE 'UTC')
    AND o.end_at   > ((now() + a.from_h * interval '1 hour') AT TIME ZONE 'UTC'));

-- Past shifts that did not go to plan, to feed the alerts, the guard's shift history and the payroll.
-- (all before this week, so they do not depend on today's date)
UPDATE security_shifts s SET status = 'MISSED', late_alert_sent_at = s.start_at AT TIME ZONE 'UTC' + interval '15 minutes',
       no_show_alert_sent_at = s.start_at AT TIME ZONE 'UTC' + interval '45 minutes'
FROM _roster r WHERE s.id = pg_temp.did(7, r.n) AND r.site = 3 AND r.day_off = -12;
UPDATE security_shifts s SET status = 'MISSED', late_alert_sent_at = (r.start_ts + interval '15 minutes') AT TIME ZONE 'UTC',
       no_show_alert_sent_at = (r.start_ts + interval '45 minutes') AT TIME ZONE 'UTC',
       no_show_dismissed_at = (r.start_ts + interval '1 hour') AT TIME ZONE 'UTC', no_show_dismissed_by = pg_temp.did(3, 1),
       no_show_dismiss_reason = 'Guard was at the hospital; the supervisor covered the post.'
FROM _roster r WHERE s.id = pg_temp.did(7, r.n) AND r.site = 2 AND r.hour = 6 AND r.day_off = -5;
UPDATE security_shifts s SET status = 'PULLED', pulled_at = (r.start_ts + interval '3 hours') AT TIME ZONE 'UTC', pulled_by = pg_temp.did(3, 1),
       pull_reason = 'Client asked for the guard to be removed after a complaint.'
FROM _roster r WHERE s.id = pg_temp.did(7, r.n) AND r.site = 1 AND r.hour = 22 AND r.day_off = -3;
UPDATE security_shifts s SET status = 'CANCELLED' FROM _roster r WHERE s.id = pg_temp.did(7, r.n) AND r.site = 1 AND r.hour = 14 AND r.day_off = -10;
UPDATE security_shifts s SET overtime_alert_sent_at = (r.start_ts + interval '8 hours 30 minutes') AT TIME ZONE 'UTC',
       overtime_closed_at = (r.start_ts + interval '8 hours 50 minutes') AT TIME ZONE 'UTC', overtime_closed_by = pg_temp.did(3, 1),
       overtime_close_reason = 'Handover delayed; relief arrived late.'
FROM _roster r WHERE s.id = pg_temp.did(7, r.n) AND r.site = 1 AND r.hour = 6 AND r.day_off = -2;
UPDATE security_shifts s SET late_alert_sent_at = (r.start_ts + interval '20 minutes') AT TIME ZONE 'UTC'
FROM _roster r WHERE s.id = pg_temp.did(7, r.n) AND r.site = 5 AND r.day_off = -4;

-- ── Checkpoints, routes, devices ───────────────────────────────────────────────────────────────
-- Some checkpoints carry an NFC tag or a Bluetooth beacon, one is switched off, one has never been scanned.
CREATE TEMP TABLE _cp (n int, site int, name text, seq int, active boolean, nfc text, ble text);
INSERT INTO _cp VALUES
 (1, 1, 'Main entrance',        1, true,  'DEMO-NFC-01', NULL),
 (2, 1, 'Loading bay',          2, true,  NULL,          'DEMO-BLE-02'),
 (3, 1, 'Parkade level 1',      3, true,  NULL,          NULL),
 (4, 1, 'Parkade level 2',      4, true,  NULL,          NULL),
 (5, 1, 'Roof plant room',      5, true,  'DEMO-NFC-05', NULL),
 (6, 1, 'Staff exit',           6, false, NULL,          NULL),
 (7, 2, 'Boom gate',            1, true,  NULL,          NULL),
 (8, 2, 'Gate A',               2, true,  'DEMO-NFC-08', NULL),
 (9, 2, 'Server building',      3, true,  NULL,          NULL),
 (10, 2, 'Perimeter fence east', 4, true, NULL,          'DEMO-BLE-10'),
 (11, 2, 'Perimeter fence west', 5, true, NULL,          NULL),
 (12, 3, 'Truck gate',          1, true,  NULL,          NULL),
 (13, 3, 'Dock 1 to 4',         2, true,  NULL,          NULL),
 (14, 3, 'Dock 5 to 8',         3, true,  NULL,          NULL),
 (15, 3, 'Yard fence',          4, true,  NULL,          NULL),
 (16, 3, 'Pump room',           5, true,  NULL,          NULL),
 (17, 4, 'Reception',           1, true,  NULL,          NULL),
 (18, 4, 'Basement parking',    2, true,  NULL,          NULL),
 (19, 4, 'Floor 9',             3, true,  NULL,          NULL),
 (20, 4, 'Roof access',         4, true,  NULL,          NULL),
 (21, 5, 'Lift lobby',          1, true,  NULL,          NULL),
 (22, 5, 'Parking level P1',    2, true,  NULL,          NULL),
 (23, 5, 'Service entrance',    3, true,  NULL,          NULL),
 (24, 6, 'Main gate',           1, false, NULL,          NULL),
 (25, 6, 'Warehouse door',      2, false, NULL,          NULL);

INSERT INTO security_checkpoints (id, tenant_id, site_id, name, description, qr_code, sort_order, active, created_at, updated_at, nfc_tag_uid, ble_beacon_id, qr_secret)
SELECT pg_temp.did(4, c.n), pg_temp.t(), pg_temp.did(2, c.site), c.name, 'DEMO checkpoint', 'DEMO-QR-' || lpad(c.n::text, 3, '0'), c.seq, c.active,
       pg_temp.utc_now() - interval '60 days', pg_temp.utc_now(), c.nfc, c.ble, md5('demo-cp-' || c.n)
FROM _cp c;

CREATE TEMP TABLE _route (n int, site int, name text, interval_min int, tol int, cps int[], active boolean);
INSERT INTO _route VALUES
-- Keep one ACTIVE route per site: patrol_rounds is unique on (shift, round number), so two active routes at one site
 -- would make the app fail when it generates rounds at shift start (see the guide, known issues).
 (1, 1, 'Perimeter round',  120, 15, ARRAY[1,2,3,4,5], true),
 (2, 1, 'Interior round (switched off)', 240, 20, ARRAY[3,4,1], false),
 (3, 2, 'Park round',        60, 10, ARRAY[7,8,9,10,11], true),
 (4, 3, 'Yard round',       120, 15, ARRAY[12,13,14,15,16], true),
 (5, 4, 'Floors round',     120, 15, ARRAY[17,18,19,20], true);

INSERT INTO security_patrol_routes (id, tenant_id, site_id, name, interval_minutes, tolerance_minutes, active, created_at, updated_at)
SELECT pg_temp.did(5, r.n), pg_temp.t(), pg_temp.did(2, r.site), r.name, r.interval_min, r.tol, r.active, now() - interval '60 days', now() FROM _route r;

INSERT INTO security_patrol_route_checkpoints (id, route_id, checkpoint_id, sequence, expected_minutes_after_route_start)
SELECT pg_temp.did(6, (r.n * 10 + u.ord)::int), pg_temp.did(5, r.n), pg_temp.did(4, u.cp), u.ord::int, (u.ord::int - 1) * 8
FROM _route r, LATERAL unnest(r.cps) WITH ORDINALITY AS u(cp, ord);

-- Devices (a shared tablet per site and one personal phone), and a session for each guard on duty now.
INSERT INTO security_devices (id, tenant_id, site_id, device_hardware_id, device_name, device_type, kiosk_mode_enabled, status, last_seen_at, battery_pct, created_at, updated_at, guard_id) VALUES
 (pg_temp.did(14,1), pg_temp.t(), pg_temp.did(2,1), 'DEMO-HW-0001', 'DEMO Centurion tablet',      'SHARED_SITE_DEVICE',    true,  'ACTIVE',  now() - interval '2 minutes',  86, now() - interval '60 days', now(), NULL),
 (pg_temp.did(14,2), pg_temp.t(), pg_temp.did(2,2), 'DEMO-HW-0002', 'DEMO Sandton tablet',        'SHARED_SITE_DEVICE',    true,  'ACTIVE',  now() - interval '1 minute',   54, now() - interval '60 days', now(), NULL),
 (pg_temp.did(14,3), pg_temp.t(), pg_temp.did(2,3), 'DEMO-HW-0003', 'DEMO Midrand tablet',        'SHARED_SITE_DEVICE',    true,  'ACTIVE',  now() - interval '3 hours',    12, now() - interval '60 days', now(), NULL),
 (pg_temp.did(14,4), pg_temp.t(), pg_temp.did(2,4), 'DEMO-HW-0004', 'DEMO Pretoria CBD tablet',   'SHARED_SITE_DEVICE',    true,  'ACTIVE',  now() - interval '5 minutes',  71, now() - interval '60 days', now(), NULL),
 (pg_temp.did(14,5), pg_temp.t(), pg_temp.did(2,5), 'DEMO-HW-0005', 'DEMO Rosebank tablet',       'SHARED_SITE_DEVICE',    true,  'PENDING', NULL,                          NULL, now() - interval '1 day',  now(), NULL),
 (pg_temp.did(14,6), pg_temp.t(), pg_temp.did(2,6), 'DEMO-HW-0006', 'DEMO Boksburg tablet (lost)','SHARED_SITE_DEVICE',    false, 'LOST',    now() - interval '25 days',    40, now() - interval '200 days', now(), NULL),
 (pg_temp.did(14,7), pg_temp.t(), NULL,             'DEMO-HW-0007', 'DEMO Thabo''s phone (off shift)', 'PERSONAL_GUARD_DEVICE', false, 'REVOKED', now() - interval '10 days', 63, now() - interval '90 days', now(), pg_temp.did(3,1));

CREATE TEMP TABLE _live AS
SELECT s.id AS shift_id, s.guard_id, s.site_id, s.start_at,
       row_number() OVER (ORDER BY s.id)::int AS rn,
       (SELECT regexp_replace(s.site_id::text, '^.*-', '')::int) AS site_n
FROM security_shifts s WHERE s.id::text LIKE 'd3d3d3d3-%' AND s.status = 'ACTIVE';

-- Only one session can be open per device, so each guard on duty gets a personal phone with its own session.
INSERT INTO security_devices (id, tenant_id, site_id, device_hardware_id, device_name, device_type, kiosk_mode_enabled, status, last_seen_at, battery_pct, created_at, updated_at, guard_id)
SELECT pg_temp.did(14, 100 + l.rn), pg_temp.t(), l.site_id, 'DEMO-HW-1' || lpad(l.rn::text, 3, '0'), 'DEMO guard phone ' || l.rn, 'PERSONAL_GUARD_DEVICE',
       false, 'ACTIVE', now() - (l.rn * interval '1 minute'), 95 - l.rn * 7, now() - interval '30 days', now(), l.guard_id
FROM _live l;

INSERT INTO security_device_sessions (id, tenant_id, device_id, guard_id, shift_id, started_at, start_pin_verified, start_face_match_confidence, start_geofence_ok, created_at)
SELECT pg_temp.did(15, l.rn), pg_temp.t(), pg_temp.did(14, 100 + l.rn), l.guard_id, l.shift_id,
       l.start_at AT TIME ZONE 'UTC' + interval '2 minutes', true, 0.93, true, l.start_at AT TIME ZONE 'UTC'
FROM _live l;

-- ── Patrol rounds and scans ────────────────────────────────────────────────────────────────────
-- Rounds for every shift of the last three days and the next day at sites with routes, built the way the app builds
-- them (interval minutes apart, each closing at start + interval - tolerance). Past rounds get a mix of outcomes.
CREATE TEMP TABLE _rd AS
SELECT row_number() OVER (ORDER BY s.start_at, r.n, k.i)::int AS n,
       s.id AS shift_id, s.guard_id, s.site_id, s.status AS shift_status, r.n AS route_n, r.cps,
       k.i AS round_number,
       (s.start_at AT TIME ZONE 'UTC') + (k.i - 1) * r.interval_min * interval '1 minute' AS exp_start,
       (s.start_at AT TIME ZONE 'UTC') + ((k.i - 1) * r.interval_min + r.interval_min - r.tol) * interval '1 minute' AS exp_end,
       cardinality(r.cps) AS n_cps
FROM security_shifts s
JOIN _route r ON pg_temp.did(2, r.site) = s.site_id AND r.active
CROSS JOIN LATERAL generate_series(1, (EXTRACT(EPOCH FROM (s.end_at - s.start_at)) / 60 / r.interval_min)::int) AS k(i)
WHERE s.id::text LIKE 'd3d3d3d3-%' AND s.status IN ('ACTIVE', 'COMPLETED', 'SCHEDULED')
  AND s.end_at > pg_temp.utc_now() - interval '3 days' AND s.start_at < pg_temp.utc_now() + interval '1 day';

CREATE TEMP TABLE _rd2 AS
SELECT d.*,
  CASE WHEN d.exp_end < now() THEN
         CASE (d.n * 7 + d.round_number) % 11 WHEN 0 THEN 'MISSED' WHEN 1 THEN 'PARTIAL' WHEN 2 THEN 'PARTIAL' WHEN 3 THEN 'OFF_SCHEDULE' ELSE 'COMPLETE' END
       WHEN d.exp_start <= now() THEN 'IN_PROGRESS'
       ELSE 'EXPECTED' END AS status
FROM _rd d;

INSERT INTO security_patrol_rounds (id, tenant_id, site_id, shift_id, route_id, round_number, expected_start_at, expected_end_at,
                                    started_at, completed_at, status, checkpoints_expected, checkpoints_scanned, off_schedule_reason,
                                    created_at, updated_at, off_schedule, acknowledged_by, acknowledgement_note)
SELECT pg_temp.did(8, d.n), pg_temp.t(), d.site_id, d.shift_id, pg_temp.did(5, d.route_n), d.round_number,
       d.exp_start AT TIME ZONE 'UTC', d.exp_end AT TIME ZONE 'UTC',
       CASE WHEN d.status IN ('COMPLETE', 'PARTIAL', 'IN_PROGRESS', 'OFF_SCHEDULE') THEN (d.exp_start + interval '3 minutes') AT TIME ZONE 'UTC' END,
       CASE WHEN d.status IN ('COMPLETE', 'OFF_SCHEDULE') THEN (d.exp_start + interval '38 minutes') AT TIME ZONE 'UTC' END,
       d.status, d.n_cps, 0,
       CASE WHEN d.status = 'OFF_SCHEDULE' THEN 'Scans were all made 40 minutes before the round window opened.' END,
       (d.exp_start - interval '1 day') AT TIME ZONE 'UTC', now(), d.status = 'OFF_SCHEDULE',
       CASE WHEN d.status IN ('MISSED', 'PARTIAL') AND d.n % 3 = 0 THEN pg_temp.did(3, 1) END,
       CASE WHEN d.status IN ('MISSED', 'PARTIAL') AND d.n % 3 = 0 THEN 'DEMO note: supervisor spoke to the guard; lift was out of service.' END
FROM _rd2 d;

-- One scan per checkpoint reached, seven minutes apart. COMPLETE reaches all, PARTIAL about 60 per cent, IN_PROGRESS the first two.
INSERT INTO security_checkpoint_logs (id, tenant_id, checkpoint_id, guard_id, shift_id, scanned_at, latitude, longitude, notes, scan_type, round_id)
SELECT pg_temp.did(9, (d.n * 10 + u.ord)::int), pg_temp.t(), pg_temp.did(4, u.cp), d.guard_id, d.shift_id,
       (d.exp_start + interval '3 minutes' + (u.ord - 1) * interval '7 minutes') AT TIME ZONE 'UTC',
       s.latitude + (u.ord - 3) * 0.00012, s.longitude + (u.ord - 3) * 0.00012,
       NULL, CASE WHEN u.ord % 5 = 0 THEN 'NFC' WHEN u.ord % 4 = 0 THEN 'BLE' ELSE 'QR' END, pg_temp.did(8, d.n)
FROM _rd2 d
JOIN security_sites s ON s.id = d.site_id
CROSS JOIN LATERAL unnest(d.cps) WITH ORDINALITY AS u(cp, ord)
WHERE d.status IN ('COMPLETE', 'OFF_SCHEDULE', 'PARTIAL', 'IN_PROGRESS')
  AND u.ord <= CASE d.status WHEN 'PARTIAL' THEN GREATEST(1, (d.n_cps * 6) / 10) WHEN 'IN_PROGRESS' THEN LEAST(2, d.n_cps) ELSE d.n_cps END
  AND (d.exp_start + interval '3 minutes' + (u.ord - 1) * interval '7 minutes') <= now();

UPDATE security_patrol_rounds pr SET checkpoints_scanned = (SELECT count(*) FROM security_checkpoint_logs l WHERE l.round_id = pr.id)
WHERE pr.id::text LIKE 'd3d3d3d3-%';

-- Scans that were not part of a round (a guard checking a post between rounds), at the checkpoint with the fewest scans,
-- while the checkpoint "Staff exit" and "Pump room" stay never scanned.
INSERT INTO security_checkpoint_logs (id, tenant_id, checkpoint_id, guard_id, shift_id, scanned_at, latitude, longitude, notes, scan_type, round_id)
SELECT pg_temp.did(9, 900000 + l.rn), pg_temp.t(), pg_temp.did(4, 1 + (l.rn % 4)), l.guard_id, l.shift_id,
       pg_temp.utc_now() - (l.rn * 11) * interval '1 minute', NULL, NULL, 'DEMO unscheduled check', 'QR', NULL
FROM _live l WHERE l.site_id = pg_temp.did(2, 1);

-- ── Live positions ─────────────────────────────────────────────────────────────────────────────
-- Of the guards on duty, a third have a fresh position, a third a stale one (25 minutes old) and a third none.
INSERT INTO security_guard_location_pings (id, tenant_id, guard_id, shift_id, device_session_id, latitude, longitude, accuracy_metres, recorded_at, created_at)
SELECT pg_temp.did(16, l.rn * 10 + p.i), pg_temp.t(), l.guard_id, l.shift_id, pg_temp.did(15, l.rn),
       s.latitude + (p.i - 3) * 0.00015 + (l.rn % 3) * 0.0001, s.longitude + (p.i - 3) * 0.00015 - (l.rn % 2) * 0.0001, 6 + p.i,
       now() - CASE WHEN l.rn % 3 = 0 THEN interval '30 seconds' ELSE interval '25 minutes' END - (p.i - 1) * interval '4 minutes', now()
FROM _live l JOIN security_sites s ON s.id = l.site_id, generate_series(1, 5) AS p(i)
WHERE l.rn % 3 <> 2 AND s.latitude IS NOT NULL;

INSERT INTO security_guard_current_location (guard_id, tenant_id, shift_id, site_id, latitude, longitude, recorded_at, updated_at)
SELECT p.guard_id, pg_temp.t(), p.shift_id, s.site_id, p.latitude, p.longitude, p.recorded_at, p.recorded_at
FROM (SELECT DISTINCT ON (guard_id) * FROM security_guard_location_pings WHERE id::text LIKE 'd3d3d3d3-%' ORDER BY guard_id, recorded_at DESC) p
JOIN security_shifts s ON s.id = p.shift_id;

DROP TABLE _slot, _roster, _cp, _route, _live, _rd, _rd2;

-- ── Evidence files (a one-page DEMO PDF each; they download and open) ─────────────────────────────
CREATE FUNCTION pg_temp.demo_pdf() RETURNS bytea LANGUAGE sql IMMUTABLE AS
$f$ SELECT decode('255044462d312e340a312030206f626a0a3c3c2f547970652f436174616c6f672f50616765732032203020523e3e0a656e646f626a0a322030206f626a0a3c3c2f547970652f50616765732f4b6964735b33203020525d2f436f756e7420313e3e0a656e646f626a0a332030206f626a0a3c3c2f547970652f506167652f506172656e742032203020522f4d65646961426f785b30203020333030203136305d2f436f6e74656e74732034203020522f5265736f75726365733c3c2f466f6e743c3c2f46312035203020523e3e3e3e3e3e0a656e646f626a0a342030206f626a0a3c3c2f4c656e6774682036393e3e0a73747265616d0a4254202f463120313420546620343020313030205464202844454d4f20646f63756d656e74202d206e6f742061207265616c2063657274696669636174652920546a2045540a656e6473747265616d0a656e646f626a0a352030206f626a0a3c3c2f547970652f466f6e742f537562747970652f54797065312f42617365466f6e742f48656c7665746963613e3e0a656e646f626a0a787265660a3020360a303030303030303030302036353533352066200a30303030303030303039203030303030206e200a30303030303030303534203030303030206e200a30303030303030313035203030303030206e200a30303030303030323137203030303030206e200a30303030303030333334203030303030206e200a747261696c65720a3c3c2f53697a6520362f526f6f742031203020523e3e0a7374617274787265660a3339370a2525454f460a', 'hex') $f$;
-- Files are stored the way the default (database) file storage keeps them. If your environment uses
-- file-storage.provider=local, the seeded evidence rows will list but not download.
CREATE TEMP TABLE _ev (n int PRIMARY KEY, entity_type text, entity_id uuid, label text, file_name text);

-- ── Screening ──────────────────────────────────────────────────────────────────────────────────
-- guard, type, result, days since done, next due in days, evidence on file, sign-off decision
CREATE TEMP TABLE _scr AS
SELECT row_number() OVER ()::int AS n, * FROM (VALUES
 (1,'CRIMINAL_RECORD_CHECK','PASS',300,65,true,'CLEARED'), (1,'REFERENCE_CHECK','PASS',300,NULL,true,'CLEARED'), (1,'DRUG_TEST','PASS',120,245,true,'CLEARED'),
 (1,'POLYGRAPH','PASS',300,NULL,true,NULL), (1,'ID_VERIFICATION','PASS',300,NULL,true,NULL),
 (2,'CRIMINAL_RECORD_CHECK','PASS',200,165,true,'CLEARED'), (2,'REFERENCE_CHECK','PASS',200,NULL,true,NULL), (2,'DRUG_TEST','PASS',340,20,true,NULL),
 (3,'CRIMINAL_RECORD_CHECK','PASS',150,215,true,NULL), (3,'REFERENCE_CHECK','PASS',150,NULL,false,NULL), (3,'DRUG_TEST','PASS',100,265,true,NULL),
 (4,'CRIMINAL_RECORD_CHECK','PASS',380,-15,true,NULL), (4,'REFERENCE_CHECK','PASS',380,NULL,true,NULL), (4,'DRUG_TEST','PASS',60,305,true,NULL),
 (5,'CRIMINAL_RECORD_CHECK','PENDING',10,NULL,false,NULL), (5,'REFERENCE_CHECK','PASS',200,NULL,true,NULL), (5,'DRUG_TEST','PASS',200,165,true,NULL),
 (6,'CRIMINAL_RECORD_CHECK','PASS',250,115,true,NULL), (6,'REFERENCE_CHECK','PASS',250,NULL,true,NULL), (6,'DRUG_TEST','PASS',250,115,true,NULL),
 (7,'CRIMINAL_RECORD_CHECK','PASS',180,185,true,'CLEARED'), (7,'REFERENCE_CHECK','PASS',180,NULL,true,'CLEARED'), (7,'DRUG_TEST','PASS',90,275,true,'CLEARED'),
 (7,'PSYCHOMETRIC','PASS',180,NULL,true,NULL), (7,'CREDIT_CHECK','PASS',180,185,true,NULL),
 (8,'CRIMINAL_RECORD_CHECK','PASS',200,165,true,NULL), (8,'REFERENCE_CHECK','PASS',200,NULL,true,NULL), (8,'DRUG_TEST','FAIL',30,NULL,true,'NOT_CLEARED'),
 (10,'CRIMINAL_RECORD_CHECK','PASS',250,115,true,NULL), (10,'REFERENCE_CHECK','PASS',250,NULL,true,NULL), (10,'DRUG_TEST','INCONCLUSIVE',5,NULL,false,NULL),
 (11,'CRIMINAL_RECORD_CHECK','PASS',300,65,true,NULL), (11,'REFERENCE_CHECK','PASS',300,NULL,true,NULL), (11,'DRUG_TEST','PASS',150,215,true,NULL),
 (12,'CRIMINAL_RECORD_CHECK','PASS',200,165,true,NULL), (12,'REFERENCE_CHECK','PASS',200,NULL,true,NULL), (12,'DRUG_TEST','PASS',200,165,true,NULL),
 (13,'CRIMINAL_RECORD_CHECK','PASS',100,265,true,'CLEARED'), (13,'REFERENCE_CHECK','PASS',100,NULL,true,'CLEARED'), (13,'DRUG_TEST','PASS',100,265,true,'CLEARED'), (13,'POLYGRAPH','PASS',100,NULL,true,NULL),
 (14,'CRIMINAL_RECORD_CHECK','PASS',400,-35,true,NULL), (14,'REFERENCE_CHECK','PASS',400,NULL,true,NULL), (14,'DRUG_TEST','PASS',400,-35,true,NULL)
) AS v(guard, type, result, ago, due_in, ev, decision);

INSERT INTO security_guard_screening_records (id, tenant_id, guard_id, screening_type, reason, result, conducted_by, conducted_at, next_due_at, report_ref, notes,
                                              created_by, created_at, updated_at, provider, requested_at, decision, decision_note, decided_by, decided_by_name, decided_at)
SELECT pg_temp.did(17, s.n), pg_temp.t(), pg_temp.did(3, s.guard), s.type, CASE WHEN s.ago > 250 THEN 'ONBOARDING' ELSE 'PERIODIC' END, s.result,
       'DEMO Vetting Services', CASE WHEN s.result = 'PENDING' THEN NULL ELSE current_date - s.ago END,
       CASE WHEN s.due_in IS NULL THEN NULL ELSE current_date + s.due_in END, 'DEMO-REF-' || lpad(s.n::text, 4, '0'),
       CASE WHEN s.result = 'FAIL' THEN 'DEMO: positive result. Guard referred for counselling.' WHEN s.result = 'INCONCLUSIVE' THEN 'DEMO: sample insufficient, retest booked.' END,
       pg_temp.did(3, 1), now() - s.ago * interval '1 day', now() - s.ago * interval '1 day', 'DEMO Vetting Services', current_date - s.ago - 2,
       s.decision, CASE WHEN s.decision = 'NOT_CLEARED' THEN 'Not cleared for deployment until a clean retest.' END,
       CASE WHEN s.decision IS NOT NULL THEN pg_temp.did(3, 1) END, CASE WHEN s.decision IS NOT NULL THEN 'DEMO Thabo Mokoena' END,
       CASE WHEN s.decision IS NOT NULL THEN now() - (s.ago - 1) * interval '1 day' END
FROM _scr s;

INSERT INTO _ev SELECT s.n, 'GuardScreeningRecord', pg_temp.did(17, s.n), 'Screening evidence', lower(s.type) || '-demo.pdf' FROM _scr s WHERE s.ev;

-- ── Competencies ───────────────────────────────────────────────────────────────────────────────
-- guard, type, title, issued days ago, expires in days (null = no expiry), required for deployment, verified (needs a file)
CREATE TEMP TABLE _cmp AS
SELECT row_number() OVER ()::int AS n, * FROM (VALUES
 (1,'FIREARM_COMPETENCY','Firearm competency (handgun)',500,300,true,true), (1,'FIRST_AID','First aid level 2',200,165,false,true), (1,'FIREFIGHTING','Firefighting',700,-20,false,true),
 (2,'FIRST_AID','First aid level 1',340,25,false,true),
 (3,'FIREARM_COMPETENCY','Firearm competency (handgun)',320,45,true,true),
 (4,'CONTROL_ROOM','Control room operator',200,NULL,false,false),
 (5,'FIREARM_COMPETENCY','Firearm competency (handgun)',350,10,true,false),
 (7,'CLOSE_PROTECTION','Close protection operator',400,250,true,true), (7,'DRIVER','Advanced driving',300,600,false,true), (7,'FIRST_AID','First aid level 2',150,215,false,true),
 (8,'FIRST_AID','First aid level 1',100,265,false,false),
 (10,'CCTV','CCTV operator',200,NULL,false,true), (10,'ACCESS_CONTROL','Access control',150,NULL,false,false),
 (13,'CLOSE_PROTECTION','Close protection team leader',500,200,true,true), (13,'VIP_PROTECTION','VIP protection',400,330,false,true), (13,'FIREARM_COMPETENCY','Firearm competency (handgun)',165,200,true,true)
) AS v(guard, type, title, issued_ago, expires_in, required, verified);

INSERT INTO security_guard_competencies (id, tenant_id, guard_id, competency_type, title, issued_by, issue_date, expiry_date, certificate_ref, required, notes,
                                         verified_by, verified_by_name, verified_at, verification_note, created_by, created_at, updated_at)
SELECT pg_temp.did(18, c.n), pg_temp.t(), pg_temp.did(3, c.guard), c.type, c.title, 'DEMO Training Academy', current_date - c.issued_ago,
       CASE WHEN c.expires_in IS NULL THEN NULL ELSE current_date + c.expires_in END, 'DEMO-CERT-' || lpad(c.n::text, 4, '0'), c.required, NULL,
       CASE WHEN c.verified THEN pg_temp.did(3, 1) END, CASE WHEN c.verified THEN 'DEMO Thabo Mokoena' END,
       CASE WHEN c.verified THEN now() - interval '30 days' END, CASE WHEN c.verified THEN 'Original sighted.' END,
       pg_temp.did(3, 1), now() - interval '60 days', now()
FROM _cmp c;

INSERT INTO _ev SELECT 1000 + c.n, 'GuardCompetency', pg_temp.did(18, c.n), 'Certificate', lower(c.type) || '-certificate-demo.pdf' FROM _cmp c WHERE c.verified;

-- Store the files and the evidence rows that point at them.
INSERT INTO stored_files (storage_key, content_type, content, size_bytes, created_at)
SELECT 'evidence/security/' || pg_temp.t() || '/d3d3d3d3-' || lpad(e.n::text, 4, '0') || '-demo.pdf', 'application/pdf', pg_temp.demo_pdf(), length(pg_temp.demo_pdf()), now()
FROM _ev e;
INSERT INTO evidence (id, tenant_id, file_name, content_type, file_size_bytes, storage_key, evidence_type, source_module, related_entity_type, related_entity_id,
                      file_hash, version, status, uploaded_by, uploaded_by_name, created_at, updated_at)
SELECT pg_temp.did(60, e.n), pg_temp.t(), e.file_name, 'application/pdf', length(pg_temp.demo_pdf()),
       'evidence/security/' || pg_temp.t() || '/d3d3d3d3-' || lpad(e.n::text, 4, '0') || '-demo.pdf', e.label, 'security', e.entity_type, e.entity_id,
       encode(sha256(pg_temp.demo_pdf()), 'hex'), 1, 'ACTIVE', pg_temp.did(3, 1), 'DEMO Thabo Mokoena', now() - interval '30 days', now()
FROM _ev e;

-- ── Guard file documents ───────────────────────────────────────────────────────────────────────
-- A one-page demo PDF is embedded in the address, so "open" works without any file storage.
CREATE TEMP TABLE _doc AS
SELECT row_number() OVER ()::int AS n, g.g AS guard, c.cat
FROM generate_series(1, 14) AS g(g)
CROSS JOIN (VALUES ('ID_COPY'), ('PSIRA_CERTIFICATE'), ('PROOF_OF_ADDRESS'), ('BANK_CONFIRMATION')) AS c(cat)
WHERE NOT (g.g = 6 AND c.cat = 'ID_COPY')           -- guard 6 has no ID copy on file (readiness shows it missing)
  AND NOT (g.g = 9 AND c.cat <> 'ID_COPY')          -- the new hire has only an ID copy so far
  AND NOT (g.g IN (11, 12, 14) AND c.cat = 'BANK_CONFIRMATION');
INSERT INTO security_guard_documents (id, tenant_id, guard_id, category, file_url, file_name, notes, uploaded_by, created_at)
SELECT pg_temp.did(19, d.n), pg_temp.t(), pg_temp.did(3, d.guard), d.cat, 'data:application/pdf;base64,JVBERi0xLjAKMSAwIG9iajw8L1R5cGUvQ2F0YWxvZy9QYWdlcyAyIDAgUj4+ZW5kb2JqCjIgMCBvYmo8PC9UeXBlL1BhZ2VzL0tpZHNbMyAwIFJdL0NvdW50IDE+PmVuZG9iagozIDAgb2JqPDwvVHlwZS9QYWdlL1BhcmVudCAyIDAgUi9NZWRpYUJveFswIDAgOTkgNjBdL0NvbnRlbnRzIDQgMCBSL1Jlc291cmNlczw8L0ZvbnQ8PC9GMTw8L1N1YnR5cGUvVHlwZTEvQmFzZUZvbnQvSGVsdmV0aWNhPj4+Pj4+Pj5lbmRvYmoKNCAwIG9iajw8L0xlbmd0aCAzMz4+c3RyZWFtCkJUIC9GMSAxMSBUZiA4IDQwIFRkIChERU1PKSBUaiBFVAplbmRzdHJlYW0gZW5kb2JqCnRyYWlsZXI8PC9Sb290IDEgMCBSPj4=', lower(d.cat) || '-demo.pdf', 'DEMO document', pg_temp.did(3, 1), now() - interval '90 days'
FROM _doc d;
INSERT INTO security_guard_documents (id, tenant_id, guard_id, category, file_url, file_name, notes, uploaded_by, created_at, deleted_at, deleted_by, delete_reason)
VALUES (pg_temp.did(19, 900), pg_temp.t(), pg_temp.did(3, 4), 'PROOF_OF_ADDRESS', 'data:application/pdf;base64,JVBERi0xLjAKMSAwIG9iajw8L1R5cGUvQ2F0YWxvZy9QYWdlcyAyIDAgUj4+ZW5kb2JqCjIgMCBvYmo8PC9UeXBlL1BhZ2VzL0tpZHNbMyAwIFJdL0NvdW50IDE+PmVuZG9iagozIDAgb2JqPDwvVHlwZS9QYWdlL1BhcmVudCAyIDAgUi9NZWRpYUJveFswIDAgOTkgNjBdL0NvbnRlbnRzIDQgMCBSL1Jlc291cmNlczw8L0ZvbnQ8PC9GMTw8L1N1YnR5cGUvVHlwZTEvQmFzZUZvbnQvSGVsdmV0aWNhPj4+Pj4+Pj5lbmRvYmoKNCAwIG9iajw8L0xlbmd0aCAzMz4+c3RyZWFtCkJUIC9GMSAxMSBUZiA4IDQwIFRkIChERU1PKSBUaiBFVAplbmRzdHJlYW0gZW5kb2JqCnRyYWlsZXI8PC9Sb290IDEgMCBSPj4=', 'old-address-demo.pdf', 'DEMO document', pg_temp.did(3, 1),
        now() - interval '300 days', now() - interval '20 days', pg_temp.did(3, 1), 'Replaced by a newer proof of address.');

-- ── Ratings ────────────────────────────────────────────────────────────────────────────────────
-- Six scores from 1 to 5. Each guard has a typical level; scores drift by one around it. Guards 6, 9 and 12 get none, so
-- their operational score shows "not enough data".
CREATE TEMP TABLE _rate AS
SELECT row_number() OVER ()::int AS n, g.guard, g.base, g.site, i.i
FROM (VALUES (1,5,4),(2,4,1),(3,4,2),(4,3,1),(5,3,3),(7,4,5),(8,2,1),(10,4,4),(11,3,2),(13,5,2)) AS g(guard, base, site)
CROSS JOIN generate_series(1, 3) AS i(i);
INSERT INTO security_guard_ratings (id, tenant_id, guard_id, site_id, source, rater_name, rated_on, punctuality, professionalism, appearance, communication, alertness, incident_handling, comment, created_by, created_by_name, created_at)
SELECT pg_temp.did(20, r.n), pg_temp.t(), pg_temp.did(3, r.guard), pg_temp.did(2, r.site), CASE WHEN r.i = 2 THEN 'SUPERVISOR' ELSE 'CLIENT' END,
       CASE WHEN r.i = 2 THEN 'DEMO Thabo Mokoena' ELSE 'DEMO Client representative' END, current_date - (r.i * 28 + r.guard),
       LEAST(5, GREATEST(1, r.base + ((r.n + 0) % 3) - 1)), LEAST(5, GREATEST(1, r.base + ((r.n + 1) % 3) - 1)), LEAST(5, GREATEST(1, r.base + ((r.n + 2) % 3) - 1)),
       LEAST(5, GREATEST(1, r.base + ((r.n + 3) % 3) - 1)), LEAST(5, GREATEST(1, r.base + ((r.n + 4) % 3) - 1)), LEAST(5, GREATEST(1, r.base + ((r.n + 5) % 3) - 1)),
       CASE WHEN r.base >= 4 THEN 'DEMO: consistently professional.' WHEN r.base <= 2 THEN 'DEMO: several concerns raised this month.' END,
       pg_temp.did(3, 1), 'DEMO Thabo Mokoena', now() - (r.i * 28 + r.guard) * interval '1 day'
FROM _rate r;

-- ── Complaints and their timelines ─────────────────────────────────────────────────────────────
-- A complaint in every state of the workflow.
CREATE TEMP TABLE _cmpl (n int, guard int, site int, ago int, category text, severity text, ctype text, status text,
                         finding text, action text, descr text, withdrawn text);
INSERT INTO _cmpl VALUES
 (1, 5, 3,  60, 'LATENESS',                 'LOW',      'CLIENT',     'CLOSED',              'SUBSTANTIATED',   'VERBAL_WARNING',  'Arrived 40 minutes late three times in one week.', NULL),
 (2, 8, 1,  12, 'SLEEPING_ON_DUTY',        'HIGH',     'CLIENT',     'UNDER_INVESTIGATION', NULL,              NULL,              'Guard found asleep in the CCTV room at 02:30.', NULL),
 (3, 8, 1,  35, 'FAILURE_TO_PATROL',       'MEDIUM',   'SUPERVISOR', 'ACTION_TAKEN',        'SUBSTANTIATED',   'WRITTEN_WARNING', 'Two rounds skipped; scans made in a batch at the end of the shift.', NULL),
 (4, 4, 1,   3, 'POOR_CUSTOMER_SERVICE',   'LOW',      'MEMBER_OF_PUBLIC', 'RECEIVED',      NULL,              NULL,              'Shopper says the guard was rude when asked for directions.', NULL),
 (5, 11, 5, 20, 'MISCONDUCT',              'HIGH',     'CLIENT',     'ACTION_TAKEN',        'SUBSTANTIATED',   'DISCIPLINARY_HEARING', 'Left the lift lobby unattended and let an unregistered contractor through.', NULL),
 (6, 3, 2,  90, 'THEFT',                   'CRITICAL', 'CLIENT',     'CLOSED',              'UNSUBSTANTIATED', 'NO_ACTION',       'Client alleged a laptop was taken from a visitor room; CCTV shows otherwise.', NULL),
 (7, 10, 4, 25, 'ABSENTEEISM',             'MEDIUM',   'SUPERVISOR', 'WITHDRAWN',           NULL,              NULL,              'Recorded as absent; the leave form was later found.', 'Leave form was approved but not captured on the roster.'),
 (8, 8, 1,  75, 'INTOXICATION',            'HIGH',     'COLLEAGUE',  'FINDING_MADE',        'SUBSTANTIATED',   NULL,              'Smelled of alcohol at shift start; sent home.', NULL);

INSERT INTO security_guard_complaints (id, tenant_id, complaint_number, guard_id, site_id, occurred_on, category, severity, description, complainant_type, complainant_name,
                                       complainant_contact, witnesses, status, investigator_name, finding, finding_note, finding_by_name, finding_at, action, action_note,
                                       action_by_name, action_at, resolution_note, closed_by_name, closed_at, withdrawn_reason, created_by, created_by_name, created_at, updated_at)
SELECT pg_temp.did(21, c.n), pg_temp.t(), 'CMP-DEMO-' || lpad(c.n::text, 2, '0'), pg_temp.did(3, c.guard), pg_temp.did(2, c.site), current_date - c.ago, c.category, c.severity, c.descr,
       c.ctype, 'DEMO complainant', '082 000 0900', CASE WHEN c.n IN (2, 5) THEN 'DEMO colleague on shift' END, c.status,
       CASE WHEN c.status NOT IN ('RECEIVED', 'WITHDRAWN') THEN 'DEMO Thabo Mokoena' END,
       c.finding, CASE WHEN c.finding IS NOT NULL THEN 'DEMO: finding recorded after reviewing CCTV, scan logs and statements.' END,
       CASE WHEN c.finding IS NOT NULL THEN 'DEMO Thabo Mokoena' END, CASE WHEN c.finding IS NOT NULL THEN now() - (c.ago - 6) * interval '1 day' END,
       c.action, CASE WHEN c.action IS NOT NULL THEN 'DEMO: action explained to the guard and signed.' END,
       CASE WHEN c.action IS NOT NULL THEN 'DEMO Thabo Mokoena' END, CASE WHEN c.action IS NOT NULL THEN now() - (c.ago - 8) * interval '1 day' END,
       CASE WHEN c.status = 'CLOSED' THEN 'DEMO: closed after the guard acknowledged the outcome.' END,
       CASE WHEN c.status = 'CLOSED' THEN 'DEMO Thabo Mokoena' END, CASE WHEN c.status = 'CLOSED' THEN now() - (c.ago - 10) * interval '1 day' END,
       c.withdrawn, pg_temp.did(3, 1), 'DEMO Thabo Mokoena', now() - c.ago * interval '1 day', now()
FROM _cmpl c;

-- Timeline: logged, then each step the status implies.
INSERT INTO security_guard_complaint_events (id, tenant_id, complaint_id, event_type, to_status, note, by_name, by_user, at)
SELECT pg_temp.did(22, (c.n * 10 + e.k)::int), pg_temp.t(), pg_temp.did(21, c.n), e.type, e.to_status, e.note, 'DEMO Thabo Mokoena', pg_temp.did(3, 1), now() - (c.ago - e.day) * interval '1 day'
FROM _cmpl c
JOIN LATERAL (VALUES
  (1, 0, 'LOGGED', 'RECEIVED', NULL::text, true),
  (2, 2, 'INVESTIGATION_STARTED', 'UNDER_INVESTIGATION', 'Statements requested.', c.status NOT IN ('RECEIVED', 'WITHDRAWN')),
  (3, 6, 'FINDING_RECORDED', 'FINDING_MADE', lower(coalesce(c.finding, '')), c.finding IS NOT NULL),
  (4, 8, 'ACTION_RECORDED', 'ACTION_TAKEN', lower(replace(coalesce(c.action, ''), '_', ' ')), c.action IS NOT NULL),
  (5, 10, 'CLOSED', 'CLOSED', 'Closed after the guard acknowledged the outcome.', c.status = 'CLOSED'),
  (6, 3, 'WITHDRAWN', 'WITHDRAWN', c.withdrawn, c.status = 'WITHDRAWN')
) AS e(k, day, type, to_status, note, applies) ON e.applies;

-- ── Risk rules (the defaults) ─────────────────────────────────────────────────────────────────
INSERT INTO security_risk_settings (id, tenant_id, review_at, warning_at, investigation_at, window_days, misconduct_at, misconduct_window_days, suspension_review_on_critical, updated_at, updated_by_name)
VALUES (pg_temp.did(24, 1), pg_temp.t(), 1, 3, 5, 90, 2, 365, true, now(), 'DEMO')
ON CONFLICT DO NOTHING;

DROP TABLE _ev, _scr, _cmp, _doc, _rate, _cmpl;

-- ── Incidents ──────────────────────────────────────────────────────────────────────────────────
-- Every status and several severities, some old enough to look neglected. A critical fire alarm is open right now.
CREATE TEMP TABLE _inc (n int, site int, guard int, type text, severity text, title text, descr text, hrs_ago numeric,
                        ack_after_min int, resolve_after_min int, assignee text, escalated boolean, reopened boolean);
INSERT INTO _inc VALUES
 (1,  1, 8,  'THEFT',      'HIGH',     'Handbag stolen from food court',        'A shopper reports a handbag taken from a table at the food court. Description of suspect obtained.', 50,  NULL, NULL, NULL, false, false),
 (2,  2, 3,  'TRESPASS',   'MEDIUM',   'Person on the east fence line',         'Person seen climbing the east fence after hours; left when challenged.', 30,   6, NULL, 'Thabo Mokoena', false, false),
 (3,  1, 2,  'MEDICAL',    'HIGH',     'Shopper collapsed near the escalators', 'Shopper collapsed; first aid given until the ambulance arrived.', 120,   4, 55, 'Lerato Dlamini', false, false),
 (4,  1, 13, 'FIRE',       'CRITICAL', 'Smoke alarm in the roof plant room',    'Smoke alarm activated on the roof. Guard went to investigate and called the fire department.', 0.7, NULL, NULL, NULL, true, false),
 (5,  3, 5,  'VANDALISM',  'LOW',      'Graffiti on the dock 3 wall',           'Fresh graffiti found at the start of shift. No suspect seen.', 200,  30, 240, 'Pieter van Wyk', false, false),
 (6,  2, 13, 'ASSAULT',    'HIGH',     'Scuffle at the boom gate',              'Two contractors fought at the boom gate; one guard slightly injured.', 70,  3, NULL, 'Thabo Mokoena', true, false),
 (7,  4, 1,  'SUSPICIOUS', 'LOW',      'Unmarked van parked outside basement',  'White van parked for three hours with two occupants. Registration noted.', 20, NULL, NULL, NULL, false, false),
 (8,  5, 7,  'GENERAL',    'LOW',      'Lift out of service',                   'Lift 2 stopped between floors; no one inside. Building management informed.', 300, 10, 90, 'Johan Pretorius', false, false),
 (9,  3, 3,  'OTHER',      'MEDIUM',    'Truck seal broken on arrival',          'Seal number did not match the waybill. Driver detained pending a call to the client.', 400, 12, 120, 'Thabo Mokoena', false, true),
 (10, 1, 8,  'THEFT',      'MEDIUM',    'Stock missing from the loading bay',    'Two pallets short during the weekly stock count.', 500, 60, 1500, 'Thabo Mokoena', false, false),
 (11, 2, 3,  'SUSPICIOUS', 'MEDIUM',    'Visitor refused to sign in',            'Visitor refused to leave an ID copy; was turned away.', 250, 5, 40, 'Lerato Dlamini', false, false),
 (12, 3, 5,  'TRESPASS',   'LOW',       'Children playing on the yard fence',    'Group of children on the fence; moved on.', 240, NULL, NULL, NULL, false, false);

INSERT INTO security_incidents (id, tenant_id, site_id, shift_id, guard_id, type, severity, description, occurred_at, resolved_at, photo_urls, created_at, updated_at,
                                acknowledged_at, latitude, longitude, status, title, acknowledged_by, resolved_by, assignee_name, assigned_at)
SELECT pg_temp.did(10, i.n), pg_temp.t(), pg_temp.did(2, i.site), NULL, pg_temp.did(3, i.guard), i.type,
       CASE WHEN i.escalated AND i.n = 6 THEN 'CRITICAL' ELSE i.severity END, i.descr,
       (now() - i.hrs_ago * interval '1 hour') AT TIME ZONE 'UTC',
       CASE WHEN i.resolve_after_min IS NOT NULL AND NOT i.reopened THEN (now() - i.hrs_ago * interval '1 hour' + i.resolve_after_min * interval '1 minute') AT TIME ZONE 'UTC' END,
       NULL, (now() - i.hrs_ago * interval '1 hour') AT TIME ZONE 'UTC', pg_temp.utc_now(),
       CASE WHEN i.ack_after_min IS NOT NULL THEN now() - i.hrs_ago * interval '1 hour' + i.ack_after_min * interval '1 minute' END,
       s.latitude, s.longitude,
       CASE WHEN i.resolve_after_min IS NOT NULL AND NOT i.reopened THEN 'RESOLVED' WHEN i.ack_after_min IS NOT NULL THEN 'ACKNOWLEDGED' ELSE 'OPEN' END,
       i.title, CASE WHEN i.ack_after_min IS NOT NULL THEN pg_temp.did(3, 1) END,
       CASE WHEN i.resolve_after_min IS NOT NULL AND NOT i.reopened THEN pg_temp.did(3, 1) END,
       i.assignee, CASE WHEN i.assignee IS NOT NULL THEN now() - i.hrs_ago * interval '1 hour' + (coalesce(i.ack_after_min, 5) + 2) * interval '1 minute' END
FROM _inc i JOIN security_sites s ON s.id = pg_temp.did(2, i.site);

INSERT INTO security_incident_events (id, tenant_id, incident_id, event_type, to_status, note, by_user, by_name, at)
SELECT pg_temp.did(11, (i.n * 10 + e.k)::int), pg_temp.t(), pg_temp.did(10, i.n), e.type, e.to_status, e.note,
       CASE WHEN e.k = 1 THEN pg_temp.did(3, i.guard) ELSE pg_temp.did(3, 1) END, CASE WHEN e.k = 1 THEN 'DEMO guard' ELSE 'DEMO Thabo Mokoena' END,
       now() - i.hrs_ago * interval '1 hour' + e.min * interval '1 minute'
FROM _inc i
JOIN LATERAL (VALUES
  (1, 0,  'REPORTED',     'OPEN',         NULL::text, true),
  (2, coalesce(i.ack_after_min, 0), 'ACKNOWLEDGED', 'ACKNOWLEDGED', NULL, i.ack_after_min IS NOT NULL),
  (3, coalesce(i.ack_after_min, 0) + 2, 'ASSIGNED', NULL, 'Assigned to ' || coalesce(i.assignee, ''), i.assignee IS NOT NULL),
  (4, coalesce(i.ack_after_min, 0) + 5, 'NOTE', NULL, 'DEMO note: client informed by phone.', i.n IN (2, 3, 6, 9)),
  (5, coalesce(i.ack_after_min, 0) + 8, 'ESCALATED', NULL, CASE WHEN i.n = 6 THEN 'Severity high to critical: a guard was injured.' ELSE 'Escalated to the duty manager: fire brigade called.' END, i.escalated),
  (6, i.resolve_after_min, 'RESOLVED', 'RESOLVED', 'DEMO: resolved on site.', i.resolve_after_min IS NOT NULL),
  (7, i.resolve_after_min + 120, 'REOPENED', 'ACKNOWLEDGED', 'Client disputes the outcome; reopened for review.', i.reopened)
) AS e(k, min, type, to_status, note, applies) ON e.applies;

-- ── Gate register ──────────────────────────────────────────────────────────────────────────────
INSERT INTO security_access_points (id, tenant_id, site_id, name, description, active, created_at, updated_at) VALUES
 (pg_temp.did(12,1), pg_temp.t(), pg_temp.did(2,1), 'Main gate',       'Public entrance',         true,  now(), now()),
 (pg_temp.did(12,2), pg_temp.t(), pg_temp.did(2,1), 'Loading bay',     'Deliveries and service',  true,  now(), now()),
 (pg_temp.did(12,3), pg_temp.t(), pg_temp.did(2,2), 'Boom gate',       'Vehicle entrance',        true,  now(), now()),
 (pg_temp.did(12,4), pg_temp.t(), pg_temp.did(2,2), 'Pedestrian gate', 'Walk-in visitors',        true,  now(), now()),
 (pg_temp.did(12,5), pg_temp.t(), pg_temp.did(2,3), 'Truck gate',      'Trucks and trailers',     true,  now(), now()),
 (pg_temp.did(12,6), pg_temp.t(), pg_temp.did(2,2), 'Old service gate','Closed after the park refit', false, now() - interval '200 days', now());

CREATE TEMP TABLE _gate (n int, site int, ap int, type text, person text, company text, host text, vehicle text, in_min int, out_min int, status text);
INSERT INTO _gate VALUES
 -- on site now
 (1, 1, 1, 'VISITOR',    'DEMO Ann Botha',       'Acme Legal',           'Mr Naidoo',      NULL,          25,   NULL, 'ON_SITE'),
 (2, 1, 2, 'DELIVERY',   'DEMO Pieter Joubert',  'Freshline Foods',      NULL,             'CY 12-345 GP', 45,  NULL, 'ON_SITE'),
 (3, 1, 1, 'CONTRACTOR', 'DEMO Thandi Nkosi',    'Cool Air HVAC',        'Facilities',     'CA 889-221',  310, NULL, 'ON_SITE'),
 (4, 2, 3, 'VISITOR',    'DEMO Sam Peters',      'Peters and Co',        'Ms Pillay',      'GP 22 AB GP',  8,   NULL, 'ON_SITE'),
 (5, 2, 4, 'VISITOR',    'DEMO Lerato Naidoo',   NULL,                   'Unit 14',        NULL,          95,  NULL, 'ON_SITE'),
 (6, 2, 3, 'CONTRACTOR', 'DEMO Sipho Dube',      'Fixit Maintenance',    'Estate manager', 'GP 44 ZZ GP', 900,  NULL, 'OVERSTAYED'),
 (7, 3, 5, 'DELIVERY',   'DEMO Kobus Smit',      'Swift Freight',        NULL,             'DL 33 PP GP', 60,   NULL, 'ON_SITE'),
 -- departed today and yesterday
 (8, 1, 1, 'VISITOR',    'DEMO Zodwa Mthembu',   'Mall tenant',          'Mr Naidoo',      NULL,          200,  150, 'DEPARTED'),
 (9, 1, 2, 'DELIVERY',   'DEMO Frans Coetzee',   'Bidvest',              NULL,             'BB 11 CC GP', 260,  235, 'DEPARTED'),
 (10, 2, 3, 'STAFF_VEHICLE', 'DEMO Maria Pillay','Park staff',           'Ms Pillay',      'GP 77 KL GP', 400,  50,  'DEPARTED'),
 (11, 2, 4, 'VISITOR',   'DEMO Ben Louw',        'Louw Consulting',      'Ms Pillay',      NULL,          330,  240, 'DEPARTED'),
 (12, 3, 5, 'DELIVERY',  'DEMO Ruan Smit',       'Swift Freight',        NULL,             'DL 55 PP GP', 500,  470, 'DEPARTED'),
 (13, 3, 5, 'CONTRACTOR','DEMO Eli Zwane',       'Pallet Repairs',       'Mr Joubert',     'GP 12 AA GP', 600,  300, 'DEPARTED'),
 (14, 1, 1, 'VISITOR',   'DEMO Karen Fourie',    NULL,                   'Mr Naidoo',      NULL,         1500, 1440, 'DEPARTED'),
 (15, 2, 3, 'OTHER',     'DEMO Lindiwe Zulu',    'Courier',              NULL,             'MT 90 UU GP', 1600, 1590, 'DEPARTED'),
 (16, 1, 2, 'DELIVERY',  'DEMO Hennie Venter',   'Makro',                NULL,             'MK 76 RR GP', 1700, 1650, 'DEPARTED');

INSERT INTO security_gate_register_entries (id, tenant_id, site_id, access_point_id, entry_type, person_name, id_number, phone, company, host_name, host_contact, purpose,
                                            vehicle_registration, vehicle_make_model, driver_name, id_scan_confidence, logged_in_by_guard_id, logged_in_at, logged_out_by_guard_id,
                                            logged_out_at, status, overstay_alert_sent_at, created_at, updated_at)
SELECT pg_temp.did(13, g.n), pg_temp.t(), pg_temp.did(2, g.site), pg_temp.did(12, g.ap), g.type, g.person, 'DEMO-ID', '082 000 08' || lpad(g.n::text, 2, '0'), g.company, g.host,
       CASE WHEN g.host IS NOT NULL THEN '082 000 0001' END,
       CASE g.type WHEN 'VISITOR' THEN 'Meeting' WHEN 'DELIVERY' THEN 'Delivery' WHEN 'CONTRACTOR' THEN 'Maintenance' ELSE 'Other' END,
       g.vehicle, CASE WHEN g.vehicle IS NOT NULL THEN 'DEMO vehicle' END, CASE WHEN g.vehicle IS NOT NULL THEN g.person END,
       CASE WHEN g.n % 2 = 0 THEN 'HIGH' ELSE 'MEDIUM' END,
       pg_temp.did(3, CASE g.site WHEN 1 THEN 2 WHEN 2 THEN 3 ELSE 5 END), now() - g.in_min * interval '1 minute',
       CASE WHEN g.out_min IS NOT NULL THEN pg_temp.did(3, CASE g.site WHEN 1 THEN 2 WHEN 2 THEN 3 ELSE 5 END) END,
       CASE WHEN g.out_min IS NOT NULL THEN now() - g.out_min * interval '1 minute' END, g.status,
       CASE WHEN g.status = 'OVERSTAYED' THEN now() - interval '2 hours' END, now() - g.in_min * interval '1 minute', now()
FROM _gate g;

-- ── Report history ─────────────────────────────────────────────────────────────────────────────
INSERT INTO security_report_runs (id, tenant_id, report_key, period, subject, format, generated_by_id, generated_by_name, generated_at) VALUES
 (pg_temp.did(23,1), pg_temp.t(), 'monthly-summary',  to_char(current_date - interval '1 month', 'YYYY-MM'), NULL,                   'PDF',  pg_temp.did(3,1), 'DEMO Thabo Mokoena', now() - interval '2 hours'),
 (pg_temp.did(23,2), pg_temp.t(), 'site-coverage',    to_char(current_date - interval '1 month', 'YYYY-MM'), 'DEMO Centurion Mall',  'VIEW', pg_temp.did(3,1), 'DEMO Thabo Mokoena', now() - interval '1 day'),
 (pg_temp.did(23,3), pg_temp.t(), 'site-coverage',    to_char(current_date - interval '1 month', 'YYYY-MM'), 'DEMO Sandton Business Park', 'PDF', pg_temp.did(3,1), 'DEMO Thabo Mokoena', now() - interval '1 day 1 hour'),
 (pg_temp.did(23,4), pg_temp.t(), 'guard-attendance', to_char(current_date - interval '1 month', 'YYYY-MM'), 'DEMO Sipho Ndlovu',    'VIEW', pg_temp.did(3,1), 'DEMO Lerato Dlamini', now() - interval '3 days'),
 (pg_temp.did(23,5), pg_temp.t(), 'monthly-summary',  to_char(current_date - interval '2 months', 'YYYY-MM'), NULL,                  'VIEW', pg_temp.did(3,1), 'DEMO Lerato Dlamini', now() - interval '9 days');

-- ── Audit trail ────────────────────────────────────────────────────────────────────────────────
INSERT INTO security_audit_log (id, tenant_id, actor_id, actor_type, entity_type, entity_id, action, old_values, new_values, metadata, occurred_at) VALUES
 (pg_temp.did(49,1), pg_temp.t(), pg_temp.did(3,1), 'USER', 'GUARD', pg_temp.did(3,11), 'STATUS_CHANGED', '{"status":"ACTIVE"}', '{"status":"SUSPENDED"}', '{"note":"Pending disciplinary hearing"}', now() - interval '6 days'),
 (pg_temp.did(49,2), pg_temp.t(), pg_temp.did(3,1), 'USER', 'GUARD', pg_temp.did(3,14), 'STATUS_CHANGED', '{"status":"ACTIVE"}', '{"status":"TERMINATED"}', '{"note":"Resigned"}', now() - interval '40 days'),
 (pg_temp.did(49,3), pg_temp.t(), pg_temp.did(3,1), 'USER', 'SITE',  pg_temp.did(2,6),  'CONTRACT_TERMINATED', '{"contractStatus":"ACTIVE"}', '{"contractStatus":"TERMINATED"}', '{"reason":"Client moved in house"}', now() - interval '20 days'),
 (pg_temp.did(49,4), pg_temp.t(), pg_temp.did(3,1), 'USER', 'SHIFT', pg_temp.did(7,950), 'CREATED', NULL, '{"note":"extra cover"}', NULL, now() - interval '1 hour'),
 (pg_temp.did(49,5), pg_temp.t(), pg_temp.did(3,2), 'USER', 'INCIDENT', pg_temp.did(10,3), 'RESOLVED', '{"status":"ACKNOWLEDGED"}', '{"status":"RESOLVED"}', NULL, now() - interval '118 hours');

DROP TABLE _inc, _gate;

-- ── Post orders ────────────────────────────────────────────────────────────────────────────────
-- Centurion Mall has a current version (ACTIVE), an older one (SUPERSEDED) and a new draft. Not every guard has acknowledged.
INSERT INTO security_post_orders (id, tenant_id, site_id, post_id, version, status, effective_from, effective_to, instructions, duties, emergency_procedures,
                                  restricted_areas, access_rules, created_by, published_by, published_at, created_at, updated_at) VALUES
 (pg_temp.did(26,1), pg_temp.t(), pg_temp.did(2,1), pg_temp.did(25,1), 1, 'SUPERSEDED', now() - interval '300 days', now() - interval '60 days',
  'DEMO: greet visitors and check bags at the entrance.', 'Bag checks. Visitor log.', 'Call control room on 082 000 0100.', 'Plant room', 'No entry after 22:00 without a pass.',
  pg_temp.did(3,1), pg_temp.did(3,1), now() - interval '300 days', now() - interval '300 days', now() - interval '60 days'),
 (pg_temp.did(26,2), pg_temp.t(), pg_temp.did(2,1), pg_temp.did(25,1), 2, 'ACTIVE', now() - interval '60 days', NULL,
  'DEMO: greet visitors, check bags, and log every contractor. Escort cleaning staff to the plant room.', 'Bag checks. Visitor and contractor log. Hourly walk of the food court.',
  'Fire: sound the alarm, call the fire department (082 000 0103), then the control room. Medical: call the ambulance (082 000 0102).', 'Plant room and roof (escort only).',
  'Contractors need a signed work permit.', pg_temp.did(3,1), pg_temp.did(3,1), now() - interval '60 days', now() - interval '65 days', now() - interval '60 days'),
 (pg_temp.did(26,3), pg_temp.t(), pg_temp.did(2,1), pg_temp.did(25,1), 3, 'DRAFT', NULL, NULL,
  'DEMO draft: add a rule for after-hours deliveries.', 'Same as version 2.', 'Unchanged.', 'Plant room and roof (escort only).', 'Draft only.',
  pg_temp.did(3,1), NULL, NULL, now() - interval '2 days', now() - interval '2 days'),
 (pg_temp.did(26,4), pg_temp.t(), pg_temp.did(2,2), pg_temp.did(25,3), 1, 'ACTIVE', now() - interval '120 days', NULL,
  'DEMO: all visitors sign in at the boom gate and leave an ID copy. Armed response patrol on the hour.', 'Gate control. Hourly patrol with scans.',
  'Panic button on the guard hut wall.', 'Server building', 'Visitor passes expire at 17:00.', pg_temp.did(3,1), pg_temp.did(3,1), now() - interval '120 days', now() - interval '125 days', now() - interval '120 days');

-- Acknowledgements of the current versions (guards on those sites; some have not signed yet).
INSERT INTO security_post_order_acknowledgements (id, tenant_id, post_order_id, version, guard_id, acknowledged_at, device_hardware_id, acknowledged_offline, synced_at, created_at)
SELECT pg_temp.did(27, row_number() OVER ()::int), pg_temp.t(), a.po, a.v, pg_temp.did(3, a.guard), now() - a.d * interval '1 day', 'DEMO-HW-0001', a.off, now() - a.d * interval '1 day', now() - a.d * interval '1 day'
FROM (VALUES (pg_temp.did(26,2), 2, 2, 55, false), (pg_temp.did(26,2), 2, 4, 50, false), (pg_temp.did(26,2), 2, 10, 20, true), (pg_temp.did(26,4), 1, 3, 100, false), (pg_temp.did(26,4), 1, 13, 90, false)) AS a(po, v, guard, d, off);

-- ── Armoury ────────────────────────────────────────────────────────────────────────────────────
INSERT INTO security_armoury (id, tenant_id, firearm_serial, firearm_type, make_model, saps_license_number, license_issued_at, license_expiry, assigned_guard_id, status,
                              last_service_at, next_service_due_at, notes, created_at, updated_at) VALUES
 (pg_temp.did(28,1), pg_temp.t(), 'DEMO-FA-SN-0001', 'HANDGUN', 'DEMO 9mm pistol', 'DEMO-LIC-0001', current_date - 700, current_date + 400, pg_temp.did(3,3), 'ISSUED',      current_date - 90,  current_date + 90, NULL, now() - interval '700 days', now()),
 (pg_temp.did(28,2), pg_temp.t(), 'DEMO-FA-SN-0002', 'HANDGUN', 'DEMO 9mm pistol', 'DEMO-LIC-0002', current_date - 500, current_date + 20,  NULL,               'IN_ARMOURY', current_date - 200, current_date - 15, 'Licence renewal due; service overdue.', now() - interval '500 days', now()),
 (pg_temp.did(28,3), pg_temp.t(), 'DEMO-FA-SN-0003', 'SHOTGUN', 'DEMO 12 gauge',   'DEMO-LIC-0003', current_date - 900, current_date - 10, NULL,               'IN_ARMOURY', current_date - 100, current_date + 80, 'Licence expired 10 days ago.', now() - interval '900 days', now()),
 (pg_temp.did(28,4), pg_temp.t(), 'DEMO-FA-SN-0004', 'HANDGUN', 'DEMO 9mm pistol', 'DEMO-LIC-0004', current_date - 300, current_date + 800, pg_temp.did(3,13), 'ISSUED',     current_date - 60,  current_date + 120, NULL, now() - interval '300 days', now()),
 (pg_temp.did(28,5), pg_temp.t(), 'DEMO-FA-SN-0005', 'HANDGUN', 'DEMO .38 revolver', 'DEMO-LIC-0005', current_date - 1200, current_date + 100, NULL,              'LOST',       current_date - 400, NULL, 'Reported lost; SAPS case number DEMO-CAS-1.', now() - interval '1200 days', now());
INSERT INTO security_armoury_logs (id, tenant_id, armoury_id, guard_id, action, witnessed_by_guard_id, shift_id, occurred_at, condition_notes, created_at) VALUES
 (pg_temp.did(29,1), pg_temp.t(), pg_temp.did(28,1), pg_temp.did(3,3),  'ISSUE',  pg_temp.did(3,1), NULL, now() - interval '9 hours',  'Serviceable. 15 rounds issued.', now()),
 (pg_temp.did(29,2), pg_temp.t(), pg_temp.did(28,1), pg_temp.did(3,3),  'RETURN', pg_temp.did(3,1), NULL, now() - interval '1 day 1 hour', 'Serviceable. 15 rounds returned.', now()),
 (pg_temp.did(29,3), pg_temp.t(), pg_temp.did(28,4), pg_temp.did(3,13), 'ISSUE',  pg_temp.did(3,1), NULL, now() - interval '5 hours',  'Serviceable. 15 rounds issued.', now());

-- Radios and keys checked out to the guards on duty.
INSERT INTO security_resource_custody (id, tenant_id, session_id, guard_id, shift_id, resource_type, resource_ref, checked_out_at, checked_in_at, witnessed_by, checkout_notes, checkin_notes, condition_on_return, created_at)
SELECT pg_temp.did(30, row_number() OVER (ORDER BY ds.id)::int), pg_temp.t(), ds.id, ds.guard_id, ds.shift_id,
       CASE WHEN row_number() OVER (ORDER BY ds.id) % 2 = 0 THEN 'KEY' ELSE 'RADIO' END, 'DEMO-' || row_number() OVER (ORDER BY ds.id),
       now() - interval '3 hours', NULL, pg_temp.did(3,1), 'DEMO checked out at the start of shift', NULL, NULL, now() - interval '3 hours'
FROM security_device_sessions ds WHERE ds.id::text LIKE 'd3d3d3d3-%' ORDER BY ds.id LIMIT 3;
INSERT INTO security_resource_custody (id, tenant_id, session_id, guard_id, shift_id, resource_type, resource_ref, checked_out_at, checked_in_at, witnessed_by, checkout_notes, checkin_notes, condition_on_return, created_at)
SELECT pg_temp.did(30, 90), pg_temp.t(), ds.id, ds.guard_id, ds.shift_id, 'RADIO', 'DEMO-RADIO-DMG', now() - interval '3 days', now() - interval '3 days' + interval '8 hours', pg_temp.did(3,1),
       NULL, 'Antenna cracked.', 'DAMAGED', now() - interval '3 days'
FROM security_device_sessions ds WHERE ds.id::text LIKE 'd3d3d3d3-%' ORDER BY ds.id LIMIT 1;

-- ── Rotation patterns, swaps ───────────────────────────────────────────────────────────────────
INSERT INTO security_rotation_patterns (id, tenant_id, site_id, name, pattern_type, cycle_definition, shift_length_hours, active, created_at, updated_at) VALUES
 (pg_temp.did(31,1), pg_temp.t(), pg_temp.did(2,1), 'DEMO 4 on, 2 off',          'FIXED_DAYS_ON_OFF',     '{"onDays":4,"offDays":2}', 12, true, now(), now()),
 (pg_temp.did(31,2), pg_temp.t(), pg_temp.did(2,2), 'DEMO Day and night weeks',  'ALTERNATING_DAY_NIGHT', '{"cycleWeeks":2,"dayStart":"06:00","nightStart":"18:00"}', 12, true, now(), now()),
 (pg_temp.did(31,3), pg_temp.t(), pg_temp.did(2,3), 'DEMO Weekdays only',        'WEEKLY_FIXED',          '{"monday":"DAY","tuesday":"DAY","wednesday":"DAY","thursday":"DAY","friday":"DAY","saturday":"OFF","sunday":"OFF"}', 8, true, now(), now());
INSERT INTO security_rotation_assignments (id, tenant_id, pattern_id, guard_id, starts_at, ends_at, position_in_cycle, created_at) VALUES
 (pg_temp.did(32,1), pg_temp.t(), pg_temp.did(31,1), pg_temp.did(3,2),  current_date - 60, NULL, 1, now()),
 (pg_temp.did(32,2), pg_temp.t(), pg_temp.did(31,1), pg_temp.did(3,4),  current_date - 60, NULL, 3, now()),
 (pg_temp.did(32,3), pg_temp.t(), pg_temp.did(31,2), pg_temp.did(3,3),  current_date - 30, NULL, 1, now()),
 (pg_temp.did(32,4), pg_temp.t(), pg_temp.did(31,3), pg_temp.did(3,5),  current_date - 90, NULL, 1, now());

INSERT INTO security_shift_swap_requests (id, tenant_id, original_shift_id, requesting_guard_id, proposed_guard_id, status, proposed_accepted_at, requested_at, decided_by, decided_at,
                                          reason, rejection_reason, validation_passed, validation_notes, created_at, updated_at)
SELECT pg_temp.did(33, w.k), pg_temp.t(), sh.id, sh.guard_id, pg_temp.did(3, w.proposed), w.status,
       CASE WHEN w.status IN ('PROPOSED_ACCEPTED', 'APPROVED', 'REJECTED') THEN now() - interval '1 day' END, now() - interval '2 days',
       CASE WHEN w.status IN ('APPROVED', 'REJECTED') THEN pg_temp.did(3, 1) END, CASE WHEN w.status IN ('APPROVED', 'REJECTED') THEN now() - interval '12 hours' END,
       w.reason, CASE WHEN w.status = 'REJECTED' THEN 'The proposed guard would exceed 48 hours this week.' END, w.status <> 'REJECTED',
       CASE WHEN w.status = 'REJECTED' THEN 'Weekly hours would be 52.' ELSE 'No clashes.' END, now() - interval '2 days', now()
FROM (VALUES (1, 2, 10, 'PENDING', 'Family event.'), (2, 4, 2, 'PROPOSED_ACCEPTED', 'Medical appointment.'), (3, 3, 13, 'APPROVED', 'Exchange agreed with colleague.'), (4, 5, 1, 'REJECTED', 'Needs the day off.')) AS w(k, owner, proposed, status, reason)
CROSS JOIN LATERAL (SELECT s.id, s.guard_id FROM security_shifts s WHERE s.guard_id = pg_temp.did(3, w.owner) AND s.status = 'SCHEDULED' AND s.id::text LIKE 'd3d3d3d3-%'
                    ORDER BY s.start_at LIMIT 1) AS sh;

-- ── Cameras, alarms and response ───────────────────────────────────────────────────────────────
INSERT INTO security_cameras (id, tenant_id, site_id, name, provider, connection_config, status, last_event_at, notes, created_at, updated_at) VALUES
 (pg_temp.did(34,1), pg_temp.t(), pg_temp.did(2,1), 'DEMO Main entrance',      'HIKVISION_CLOUD', '{"note":"demo, no real device"}', 'ACTIVE',  now() - interval '15 minutes', NULL, now(), now()),
 (pg_temp.did(34,2), pg_temp.t(), pg_temp.did(2,1), 'DEMO Roof plant room',    'ONVIF',           '{"note":"demo, no real device"}', 'ACTIVE',  now() - interval '40 minutes', NULL, now(), now()),
 (pg_temp.did(34,3), pg_temp.t(), pg_temp.did(2,2), 'DEMO East fence',         'RTSP_GENERIC',    '{"note":"demo, no real device"}', 'OFFLINE', now() - interval '3 days',     'Offline since the storm.', now(), now()),
 (pg_temp.did(34,4), pg_temp.t(), pg_temp.did(2,3), 'DEMO Truck gate',         'NONE',            NULL,                              'ACTIVE',  NULL, 'Guard-operated phone camera.', now(), now());
INSERT INTO security_alarm_events (id, tenant_id, site_id, source, raw_payload, severity, status, triggered_by_guard_id, latitude, longitude, description, triaged_by, triaged_at,
                                   linked_incident_id, created_at, updated_at, camera_id) VALUES
 (pg_temp.did(35,1), pg_temp.t(), pg_temp.did(2,1), 'ALARM_PANEL',  NULL, 'CRITICAL', 'DISPATCHED', NULL, -25.8603, 28.1894, 'DEMO: smoke detector, roof plant room.', pg_temp.did(3,1), now() - interval '35 minutes', pg_temp.did(10,4), now() - interval '42 minutes', now(), NULL),
 (pg_temp.did(35,2), pg_temp.t(), pg_temp.did(2,2), 'PANIC_BUTTON', NULL, 'HIGH',     'RESOLVED',   pg_temp.did(3,13), -26.1076, 28.0567, 'DEMO: panic button pressed at the boom gate.', pg_temp.did(3,1), now() - interval '69 minutes', pg_temp.did(10,6), now() - interval '70 minutes', now(), NULL),
 (pg_temp.did(35,3), pg_temp.t(), pg_temp.did(2,1), 'CCTV_MOTION',  NULL, 'LOW',      'NEW',        NULL, NULL, NULL, 'DEMO: motion in the loading bay after hours.', NULL, NULL, NULL, now() - interval '12 minutes', now(), pg_temp.did(34,1)),
 (pg_temp.did(35,4), pg_temp.t(), pg_temp.did(2,2), 'CCTV_MOTION',  NULL, 'MEDIUM',   'FALSE_ALARM', NULL, NULL, NULL, 'DEMO: motion on the east fence camera; a cat.', pg_temp.did(3,1), now() - interval '1 day', NULL, now() - interval '1 day', now(), pg_temp.did(34,3)),
 (pg_temp.did(35,5), pg_temp.t(), pg_temp.did(2,3), 'MANUAL',       NULL, 'MEDIUM',   'TRIAGED',    pg_temp.did(3,5), NULL, NULL, 'DEMO: guard reports a suspicious truck at the gate.', pg_temp.did(3,1), now() - interval '4 hours', NULL, now() - interval '4 hours', now(), NULL),
 (pg_temp.did(35,6), pg_temp.t(), pg_temp.did(2,5), 'DURESS',       NULL, 'CRITICAL', 'RESOLVED',   pg_temp.did(3,7), NULL, NULL, 'DEMO: duress code entered in error.', pg_temp.did(3,1), now() - interval '6 days', NULL, now() - interval '6 days', now(), NULL);
INSERT INTO security_dispatches (id, tenant_id, alarm_event_id, dispatched_unit_type, dispatched_guard_id, dispatched_by, dispatched_at, arrived_at, resolved_at, outcome, resolution_notes, created_at) VALUES
 (pg_temp.did(36,1), pg_temp.t(), pg_temp.did(35,1), 'GUARD',         pg_temp.did(3,13), pg_temp.did(3,1), now() - interval '34 minutes', now() - interval '28 minutes', NULL, NULL, NULL, now() - interval '34 minutes'),
 (pg_temp.did(36,2), pg_temp.t(), pg_temp.did(35,2), 'ARMED_RESPONSE', NULL,             pg_temp.did(3,1), now() - interval '68 minutes', now() - interval '58 minutes', now() - interval '45 minutes', 'ESCALATED', 'Police attended and took statements.', now() - interval '68 minutes'),
 (pg_temp.did(36,3), pg_temp.t(), pg_temp.did(35,6), 'GUARD',         pg_temp.did(3,7), pg_temp.did(3,1), now() - interval '6 days', now() - interval '6 days' + interval '4 minutes', now() - interval '6 days' + interval '10 minutes', 'FALSE_ALARM', 'Code entered in error.', now() - interval '6 days');

-- ── Payroll ────────────────────────────────────────────────────────────────────────────────────
-- Two weekly periods built from the completed shifts of the two weeks before this one. The current week is a draft with no lines.
INSERT INTO security_payroll_periods (id, tenant_id, branch_id, name, period_type, period_start, period_end, status, total_hours, total_amount_cents, approved_by, approved_at,
                                      exported_at, export_format, notes, created_by, created_at, updated_at) VALUES
 (pg_temp.did(40,1), pg_temp.t(), NULL, 'DEMO Two weeks ago', 'WEEKLY', (pg_temp.week_start() AT TIME ZONE 'Africa/Johannesburg')::date - 14, (pg_temp.week_start() AT TIME ZONE 'Africa/Johannesburg')::date - 8,
  'PAID',     0, 0, pg_temp.did(3,1), now() - interval '9 days', now() - interval '8 days', 'CSV', NULL, pg_temp.did(3,1), now() - interval '10 days', now()),
 (pg_temp.did(40,2), pg_temp.t(), NULL, 'DEMO Last week',     'WEEKLY', (pg_temp.week_start() AT TIME ZONE 'Africa/Johannesburg')::date - 7,  (pg_temp.week_start() AT TIME ZONE 'Africa/Johannesburg')::date - 1,
  'APPROVED', 0, 0, pg_temp.did(3,1), now() - interval '1 day', NULL, NULL, 'Awaiting export.', pg_temp.did(3,1), now() - interval '2 days', now()),
 (pg_temp.did(40,3), pg_temp.t(), NULL, 'DEMO This week',     'WEEKLY', (pg_temp.week_start() AT TIME ZONE 'Africa/Johannesburg')::date,       (pg_temp.week_start() AT TIME ZONE 'Africa/Johannesburg')::date + 6,
  'DRAFT',    0, 0, NULL, NULL, NULL, NULL, NULL, pg_temp.did(3,1), now(), now());

INSERT INTO security_payroll_line_items (id, tenant_id, period_id, guard_id, shift_id, line_type, shift_start_at, shift_end_at, hours_worked, overtime_hours, hourly_rate_cents,
                                         overtime_rate_cents, gross_amount_cents, notes, created_at)
SELECT pg_temp.did(41, row_number() OVER (ORDER BY s.start_at, s.id)::int), pg_temp.t(), p.id, s.guard_id, s.id, 'REGULAR',
       s.start_at AT TIME ZONE 'UTC', s.end_at AT TIME ZONE 'UTC', 8, 0, g.hourly_rate_cents, (g.hourly_rate_cents * 3 / 2), (g.hourly_rate_cents * 8), NULL, now()
FROM security_shifts s
JOIN security_guards g ON g.id = s.guard_id
JOIN security_payroll_periods p ON p.id::text LIKE 'd3d3d3d3-%' AND p.status IN ('PAID', 'APPROVED')
     AND (s.start_at AT TIME ZONE 'UTC' AT TIME ZONE 'Africa/Johannesburg')::date BETWEEN p.period_start AND p.period_end
WHERE s.id::text LIKE 'd3d3d3d3-%' AND s.status = 'COMPLETED';
UPDATE security_payroll_periods p SET
  total_hours = coalesce((SELECT sum(hours_worked) FROM security_payroll_line_items l WHERE l.period_id = p.id), 0),
  total_amount_cents = coalesce((SELECT sum(gross_amount_cents) FROM security_payroll_line_items l WHERE l.period_id = p.id), 0)
WHERE p.id::text LIKE 'd3d3d3d3-%';

INSERT INTO security_guard_rate_history (id, tenant_id, guard_id, old_rate_cents, new_rate_cents, effective_from, reason, changed_by, created_at) VALUES
 (pg_temp.did(39,1), pg_temp.t(), pg_temp.did(3,1), 9000, 9500, current_date - 200, 'Annual increase.', pg_temp.did(3,1), now() - interval '200 days'),
 (pg_temp.did(39,2), pg_temp.t(), pg_temp.did(3,3), 6800, 7200, current_date - 200, 'Annual increase.', pg_temp.did(3,1), now() - interval '200 days'),
 (pg_temp.did(39,3), pg_temp.t(), pg_temp.did(3,9), NULL, 5600, current_date - 14,  'Starting rate.',   pg_temp.did(3,1), now() - interval '14 days');

-- ── Close protection ───────────────────────────────────────────────────────────────────────────
INSERT INTO security_principals (id, tenant_id, full_name, alias_codename, threat_level, medical_notes, known_threats, emergency_contacts, photo_url, active, created_at, updated_at, vetting_status) VALUES
 (pg_temp.did(42,1), pg_temp.t(), 'DEMO Principal One',   'BLUE HERON',  'MEDIUM',   'No known conditions.',          'Hostile press at events.',        '[{"name":"DEMO Assistant","phone":"082 000 0901"}]', NULL, true,  now() - interval '200 days', now(), 'CLEARED'),
 (pg_temp.did(42,2), pg_temp.t(), 'DEMO Principal Two',   'GREY FALCON', 'HIGH',     'Penicillin allergy.',           'Credible threat after a court case.', '[{"name":"DEMO Spouse","phone":"082 000 0902"}]', NULL, true,  now() - interval '90 days',  now(), 'PENDING'),
 (pg_temp.did(42,3), pg_temp.t(), 'DEMO Principal Three', 'RED KITE',    'CRITICAL', NULL,                            'Extortion attempt reported to SAPS.', '[]',                                                    NULL, false, now() - interval '400 days', now(), 'FLAGGED');
INSERT INTO security_principal_vetting (id, tenant_id, principal_id, vetting_type, result, conducted_by, conducted_at, next_review_at, report_ref, notes, created_by, created_at, updated_at) VALUES
 (pg_temp.did(47,1), pg_temp.t(), pg_temp.did(42,1), 'SANCTIONS_SCREENING', 'CLEAR',   'DEMO Compliance Desk', current_date - 100, current_date + 265, 'DEMO-PV-1', NULL, pg_temp.did(3,1), now(), now()),
 (pg_temp.did(47,2), pg_temp.t(), pg_temp.did(42,1), 'PEP_CHECK',           'CLEAR',   'DEMO Compliance Desk', current_date - 100, current_date + 265, 'DEMO-PV-2', NULL, pg_temp.did(3,1), now(), now()),
 (pg_temp.did(47,3), pg_temp.t(), pg_temp.did(42,2), 'SOURCE_OF_FUNDS',     'PENDING', 'DEMO Compliance Desk', NULL,               NULL,               'DEMO-PV-3', 'Documents requested.', pg_temp.did(3,1), now(), now()),
 (pg_temp.did(47,4), pg_temp.t(), pg_temp.did(42,3), 'ADVERSE_MEDIA',       'HIT',     'DEMO Compliance Desk', current_date - 300, current_date - 30,  'DEMO-PV-4', 'Adverse reports found.', pg_temp.did(3,1), now(), now());
INSERT INTO security_declined_principals (id, tenant_id, principal_id, declined_at, declined_by, reason, created_at) VALUES
 (pg_temp.did(48,1), pg_temp.t(), pg_temp.did(42,3), current_date - 30, pg_temp.did(3,1), 'DEMO: vetting returned an adverse hit; engagement declined.', now() - interval '30 days');
INSERT INTO security_protection_vehicles (id, tenant_id, vehicle_type, registration, make_model, armored, assigned_driver_guard_id, status, notes, created_at, updated_at) VALUES
 (pg_temp.did(46,1), pg_temp.t(), 'PRINCIPAL_CAR', 'DEMO 001 GP', 'DEMO armoured sedan', true,  pg_temp.did(3,7), 'IN_USE',     NULL, now(), now()),
 (pg_temp.did(46,2), pg_temp.t(), 'LEAD_CAR',      'DEMO 002 GP', 'DEMO SUV',            false, pg_temp.did(3,13), 'IN_USE',    NULL, now(), now()),
 (pg_temp.did(46,3), pg_temp.t(), 'FOLLOW_CAR',    'DEMO 003 GP', 'DEMO SUV',            false, NULL,             'IN_SERVICE', 'Brake service.', now(), now());
INSERT INTO security_protection_details (id, tenant_id, principal_id, detail_type, start_at, end_at, status, billing_rate, client_reference, notes, created_at, updated_at) VALUES
 (pg_temp.did(43,1), pg_temp.t(), pg_temp.did(42,1), 'MOBILE', now() - interval '3 hours', now() + interval '5 hours', 'ACTIVE',    4500, 'DEMO-PO-1', 'Corporate visit across Gauteng.', now() - interval '5 days', now()),
 (pg_temp.did(43,2), pg_temp.t(), pg_temp.did(42,2), 'EVENT',  now() + interval '4 days',  now() + interval '4 days 6 hours', 'PLANNED', 6000, 'DEMO-PO-2', 'Awards evening in Sandton.', now() - interval '2 days', now()),
 (pg_temp.did(43,3), pg_temp.t(), pg_temp.did(42,1), 'STATIC', now() - interval '20 days', now() - interval '19 days', 'COMPLETED', 3000, 'DEMO-PO-0', NULL, now() - interval '25 days', now());
INSERT INTO security_detail_assignments (id, tenant_id, detail_id, guard_id, role, assignment_start, assignment_end, vehicle_id, created_at) VALUES
 (pg_temp.did(44,1), pg_temp.t(), pg_temp.did(43,1), pg_temp.did(3,13), 'TEAM_LEADER', now() - interval '3 hours', now() + interval '5 hours', pg_temp.did(46,2), now()),
 (pg_temp.did(44,2), pg_temp.t(), pg_temp.did(43,1), pg_temp.did(3,7),  'DRIVER',      now() - interval '3 hours', now() + interval '5 hours', pg_temp.did(46,1), now()),
 (pg_temp.did(44,3), pg_temp.t(), pg_temp.did(43,2), pg_temp.did(3,13), 'TEAM_LEADER', now() + interval '4 days', now() + interval '4 days 6 hours', NULL, now()),
 (pg_temp.did(44,4), pg_temp.t(), pg_temp.did(43,2), pg_temp.did(3,7),  'CPO',         now() + interval '4 days', now() + interval '4 days 6 hours', NULL, now());
INSERT INTO security_itinerary_stops (id, tenant_id, detail_id, sequence, location_name, address, latitude, longitude, scheduled_arrival, scheduled_departure, actual_arrival,
                                      actual_departure, advance_survey_required, notes, created_at, updated_at) VALUES
 (pg_temp.did(45,1), pg_temp.t(), pg_temp.did(43,1), 1, 'DEMO Head office',        'Rivonia Road, Sandton',  -26.1076, 28.0567, now() - interval '3 hours', now() - interval '2 hours', now() - interval '3 hours', now() - interval '2 hours', true,  NULL, now(), now()),
 (pg_temp.did(45,2), pg_temp.t(), pg_temp.did(43,1), 2, 'DEMO Client lunch',       'Cradock Avenue, Rosebank', -26.1450, 28.0407, now() - interval '90 minutes', now() + interval '30 minutes', now() - interval '85 minutes', NULL, true, 'Table by the window.', now(), now()),
 (pg_temp.did(45,3), pg_temp.t(), pg_temp.did(43,1), 3, 'DEMO Pretoria meeting',   'Church Street, Pretoria', -25.7461, 28.1881, now() + interval '3 hours', now() + interval '5 hours', NULL, NULL, false, NULL, now(), now());
INSERT INTO security_advance_surveys (id, tenant_id, itinerary_stop_id, surveyed_by_guard_id, surveyed_at, entry_exit_routes_notes, hazards_noted, photo_urls, all_clear, created_at) VALUES
 (pg_temp.did(48,10), pg_temp.t(), pg_temp.did(45,1), pg_temp.did(3,7), now() - interval '1 day', 'Two exits: front and kitchen.', NULL, NULL, true, now() - interval '1 day'),
 (pg_temp.did(48,11), pg_temp.t(), pg_temp.did(45,2), pg_temp.did(3,7), now() - interval '1 day', 'Single entrance from the street.', 'Road works outside; one-way traffic.', NULL, false, now() - interval '1 day');


COMMIT;
