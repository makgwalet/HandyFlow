-- =============================================================================
-- V100__bookings_qa_test_data.sql  —  Bookings QA seed
-- Tenant A: f3ca02b3-eca5-4035-8756-941c72ab6512  (Acme Security Solutions)
-- Tenant B: aeb2b97f-9523-4bd7-a060-685a10c24831  (Beta Construction)
-- =============================================================================

BEGIN;

DO $$
DECLARE
    tenant_a  UUID := 'f3ca02b3-eca5-4035-8756-941c72ab6512';
    tenant_b  UUID := 'aeb2b97f-9523-4bd7-a060-685a10c24831';

    -- Services  (all hex: 0-9 a-f only)
    svc_haircut    UUID := 'b0000001-0000-0000-0000-000000000001';
    svc_colour     UUID := 'b0000001-0000-0000-0000-000000000002';
    svc_massage    UUID := 'b0000001-0000-0000-0000-000000000003';
    svc_consult    UUID := 'b0000001-0000-0000-0000-000000000004';
    svc_mani_pedi  UUID := 'b0000001-0000-0000-0000-000000000005';
    svc_facial     UUID := 'b0000001-0000-0000-0000-000000000006';
    svc_training   UUID := 'b0000001-0000-0000-0000-000000000007';
    svc_inspection UUID := 'b0000001-0000-0000-0000-000000000008';

    -- Staff
    staff_thandi UUID := 'b0000002-0000-0000-0000-000000000001';
    staff_sipho  UUID := 'b0000002-0000-0000-0000-000000000002';
    staff_lerato UUID := 'b0000002-0000-0000-0000-000000000003';
    staff_marco  UUID := 'b0000002-0000-0000-0000-000000000004';

    -- Tenant B
    svc_b_cut   UUID := 'b0000001-0000-0000-0000-000000000091';
    staff_b_one UUID := 'b0000002-0000-0000-0000-000000000091';

    -- Booking IDs  — all hex (replaced k→0, x→a, etc.)
    bk01 UUID := 'b0000003-0000-0000-0000-000000000001';
    bk02 UUID := 'b0000003-0000-0000-0000-000000000002';
    bk03 UUID := 'b0000003-0000-0000-0000-000000000003';
    bk04 UUID := 'b0000003-0000-0000-0000-000000000004';
    bk05 UUID := 'b0000003-0000-0000-0000-000000000005';
    bk06 UUID := 'b0000003-0000-0000-0000-000000000006';
    bk07 UUID := 'b0000003-0000-0000-0000-000000000007';
    bk08 UUID := 'b0000003-0000-0000-0000-000000000008';
    bk09 UUID := 'b0000003-0000-0000-0000-000000000009';
    bk10 UUID := 'b0000003-0000-0000-0000-000000000010';
    bk11 UUID := 'b0000003-0000-0000-0000-000000000011';
    bk12 UUID := 'b0000003-0000-0000-0000-000000000012';
    bk13 UUID := 'b0000003-0000-0000-0000-000000000013';
    bk14 UUID := 'b0000003-0000-0000-0000-000000000014';
    bk15 UUID := 'b0000003-0000-0000-0000-000000000015';
    bk16 UUID := 'b0000003-0000-0000-0000-000000000016';
    bk17 UUID := 'b0000003-0000-0000-0000-000000000017';
    bk18 UUID := 'b0000003-0000-0000-0000-000000000018';
    bk19 UUID := 'b0000003-0000-0000-0000-000000000019';
    bk20 UUID := 'b0000003-0000-0000-0000-000000000020';
    bk21 UUID := 'b0000003-0000-0000-0000-000000000021';
    bk22 UUID := 'b0000003-0000-0000-0000-000000000022';
    bk23 UUID := 'b0000003-0000-0000-0000-000000000023';
    bk24 UUID := 'b0000003-0000-0000-0000-000000000024';
    bk25 UUID := 'b0000003-0000-0000-0000-000000000025';
    bkb1 UUID := 'b0000003-0000-0000-0000-000000000091';
    bkb2 UUID := 'b0000003-0000-0000-0000-000000000092';

    -- Availability / block IDs
    av01 UUID := 'a0000001-0000-0000-0000-000000000001';
    av02 UUID := 'a0000001-0000-0000-0000-000000000002';
    av03 UUID := 'a0000001-0000-0000-0000-000000000003';
    av04 UUID := 'a0000001-0000-0000-0000-000000000004';
    av05 UUID := 'a0000001-0000-0000-0000-000000000005';
    av06 UUID := 'a0000001-0000-0000-0000-000000000006';
    av07 UUID := 'a0000001-0000-0000-0000-000000000007';
    av08 UUID := 'a0000001-0000-0000-0000-000000000008';
    av09 UUID := 'a0000001-0000-0000-0000-000000000009';
    avb1 UUID := 'a0000001-0000-0000-0000-000000000091';
    avb2 UUID := 'a0000001-0000-0000-0000-000000000092';
    avb3 UUID := 'a0000001-0000-0000-0000-000000000093';
    avb4 UUID := 'a0000001-0000-0000-0000-000000000094';
    avb5 UUID := 'a0000001-0000-0000-0000-000000000095';
    bl01 UUID := 'b0000004-0000-0000-0000-000000000001';
    bl02 UUID := 'b0000004-0000-0000-0000-000000000002';
    bl03 UUID := 'b0000004-0000-0000-0000-000000000003';

    -- Date anchors
    today         DATE := CURRENT_DATE;
    yesterday     DATE := CURRENT_DATE - 1;
    two_ago       DATE := CURRENT_DATE - 2;
    week_ago      DATE := CURRENT_DATE - 7;
    two_weeks     DATE := CURRENT_DATE - 14;
    month_ago     DATE := CURRENT_DATE - 30;
    tomorrow      DATE := CURRENT_DATE + 1;
    next_week     DATE := CURRENT_DATE + 7;
    two_weeks_fwd DATE := CURRENT_DATE + 14;

BEGIN

-- ── Services ──────────────────────────────────────────────────────────────────
INSERT INTO booking_services
    (id, tenant_id, name, description, duration_minutes, price, currency,
     color, active, buffer_before_minutes, buffer_after_minutes,
     min_lead_time_minutes, max_advance_days, created_at, updated_at)
VALUES
(svc_haircut,    tenant_a, 'Haircut & Style',           'Precision cut and blow-dry', 45,  350.00, 'ZAR', '#0D9488', true,  0, 10,    0,  90, NOW()-INTERVAL'60d', NOW()),
(svc_colour,     tenant_a, 'Full Colour Treatment',     'Root-to-tip colour + toner', 120, 950.00, 'ZAR', '#7C3AED', true, 15, 20,   60,  90, NOW()-INTERVAL'60d', NOW()),
(svc_massage,    tenant_a, 'Full Body Massage',         '60-min deep tissue massage',  60, 650.00, 'ZAR', '#DB2777', true, 10, 15,  120,  60, NOW()-INTERVAL'55d', NOW()),
(svc_consult,    tenant_a, 'Initial Consultation',      'Free 20-min assessment',      20,   0.00, 'ZAR', '#166534', true,  0,  0,    0,  30, NOW()-INTERVAL'50d', NOW()),
(svc_mani_pedi,  tenant_a, 'Manicure & Pedicure',       'Gel mani + classic pedi',     90, 520.00, 'ZAR', '#D97706', true,  5, 30,    0,  90, NOW()-INTERVAL'45d', NOW()),
(svc_facial,     tenant_a, 'Hydrating Facial',          'Cleanse, exfoliate, mask',    60, 480.00, 'ZAR', '#0891B2', true, 10, 15,   60,  60, NOW()-INTERVAL'40d', NOW()),
(svc_training,   tenant_a, 'Personal Training Session', '1-hour bespoke fitness',      60, 400.00, 'ZAR', '#DC2626', true,  0,  0, 1440,  60, NOW()-INTERVAL'35d', NOW()),
(svc_inspection, tenant_a, 'Property Inspection',       'Full structural inspection', 180,2500.00, 'ZAR', '#1D4ED8', true,  0,  0,  480,  30, NOW()-INTERVAL'30d', NOW());

-- ── Staff ─────────────────────────────────────────────────────────────────────
INSERT INTO booking_staff (id, tenant_id, name, email, phone, active, created_at, updated_at) VALUES
(staff_thandi, tenant_a, 'Thandi Dlamini',    'thandi@acmesecurity.co.za', '+27 82 111 2222', true, NOW()-INTERVAL'60d', NOW()),
(staff_sipho,  tenant_a, 'Sipho Nkosi',        'sipho@acmesecurity.co.za',  '+27 83 333 4444', true, NOW()-INTERVAL'60d', NOW()),
(staff_lerato, tenant_a, 'Lerato Mokoena',     'lerato@acmesecurity.co.za', '+27 71 555 6666', true, NOW()-INTERVAL'55d', NOW()),
(staff_marco,  tenant_a, 'Marco van der Berg', 'marco@acmesecurity.co.za',  '+27 72 777 8888', true, NOW()-INTERVAL'30d', NOW());

-- ── Availability  (0=Sun 1=Mon 2=Tue 3=Wed 4=Thu 5=Fri 6=Sat) ────────────────
INSERT INTO booking_availability (id, tenant_id, staff_id, day_of_week, start_time, end_time, active) VALUES
(av01, tenant_a, NULL,        1, '08:00', '17:00', true),
(av02, tenant_a, NULL,        2, '08:00', '17:00', true),
(av03, tenant_a, NULL,        3, '08:00', '17:00', true),
(av04, tenant_a, NULL,        4, '08:00', '17:00', true),
(av05, tenant_a, NULL,        5, '08:00', '17:00', true),
(av06, tenant_a, NULL,        6, '09:00', '13:00', true),
(av07, tenant_a, staff_marco, 1, '09:00', '16:00', true),
(av08, tenant_a, staff_marco, 3, '09:00', '16:00', true),
(av09, tenant_a, staff_marco, 5, '09:00', '16:00', true);

-- ── Time blocks ───────────────────────────────────────────────────────────────
INSERT INTO booking_blocks (id, tenant_id, staff_id, block_date, start_time, end_time, reason, created_at) VALUES
(bl01, tenant_a, NULL,       today,          '13:00', '14:00', 'Lunch break',          NOW()),
(bl02, tenant_a, staff_sipho, tomorrow,       '14:00', '17:00', 'Personal appointment', NOW()),
(bl03, tenant_a, NULL,       next_week + 2,  NULL,    NULL,    'Public holiday',       NOW());

-- ── Sequence primer ───────────────────────────────────────────────────────────
INSERT INTO booking_number_seq (tenant_id, year, last_seq)
VALUES (tenant_a, EXTRACT(YEAR FROM NOW())::INT, 25)
ON CONFLICT (tenant_id, year) DO UPDATE SET last_seq = GREATEST(booking_number_seq.last_seq, 25);

-- ── Bookings ──────────────────────────────────────────────────────────────────
-- Columns: id, tenant_id, booking_number, service_id, staff_id, customer_id,
--   client_name, client_email, client_phone,
--   booking_date, start_time, end_time, duration_minutes,
--   status, price, currency, notes, cancellation_reason,
--   reminder_sent, confirmed_at, completed_at, cancelled_at,
--   original_booking_date, original_start_time, rescheduled_at,
--   created_at, updated_at

INSERT INTO bookings VALUES

-- TODAY CONFIRMED (5) ──────────────────────────────────────────────────────────
(bk01, tenant_a, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00001',
 svc_haircut, staff_thandi, NULL,
 'Tau Makgwale','tau@taumining.co.za','+27 82 100 0001',
 today,'09:00','09:45',45,
 'CONFIRMED',350.00,'ZAR','Regular client - prefers no small talk',NULL,
 true,NOW()-INTERVAL'2h',NULL,NULL,
 NULL,NULL,NULL,
 NOW()-INTERVAL'3d',NOW()-INTERVAL'2h'),

(bk02, tenant_a, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00002',
 svc_facial, staff_lerato, NULL,
 'Zanele Mokoena','zanele.m@gmail.com','+27 71 200 0002',
 today,'09:30','10:30',60,
 'CONFIRMED',480.00,'ZAR','Sensitive skin - avoid exfoliant',NULL,
 true,NOW()-INTERVAL'1h',NULL,NULL,
 NULL,NULL,NULL,
 NOW()-INTERVAL'2d',NOW()-INTERVAL'1h'),

(bk03, tenant_a, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00003',
 svc_mani_pedi, staff_lerato, NULL,
 'Nomvula Khumalo','nomvula@khumalo.net','+27 83 300 0003',
 today,'10:45','12:15',90,
 'CONFIRMED',520.00,'ZAR',NULL,NULL,
 false,NOW()-INTERVAL'30min',NULL,NULL,
 NULL,NULL,NULL,
 NOW()-INTERVAL'1d',NOW()-INTERVAL'30min'),

(bk04, tenant_a, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00004',
 svc_massage, staff_sipho, NULL,
 'Thabo Sithole','thabo.s@outlook.com','+27 72 400 0004',
 today,'14:15','15:15',60,
 'CONFIRMED',650.00,'ZAR','Deep tissue, focus lower back',NULL,
 false,NOW()-INTERVAL'20min',NULL,NULL,
 NULL,NULL,NULL,
 NOW()-INTERVAL'5d',NOW()-INTERVAL'20min'),

(bk05, tenant_a, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00005',
 svc_colour, staff_thandi, NULL,
 'Priya Patel','priya.patel@hotmail.com','+27 11 500 0005',
 today,'15:30','17:30',120,
 'CONFIRMED',950.00,'ZAR','Ombre effect - reference photo on WhatsApp',NULL,
 false,NOW()-INTERVAL'10min',NULL,NULL,
 NULL,NULL,NULL,
 NOW()-INTERVAL'4d',NOW()-INTERVAL'10min'),

-- TODAY IN_PROGRESS (2) ────────────────────────────────────────────────────────
(bk06, tenant_a, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00006',
 svc_haircut, staff_thandi, NULL,
 'Kabelo Motsepe','kabelo@gmail.com','+27 82 600 0006',
 today,'08:00','08:45',45,
 'IN_PROGRESS',350.00,'ZAR',NULL,NULL,
 true,NOW()-INTERVAL'1h',NULL,NULL,
 NULL,NULL,NULL,
 NOW()-INTERVAL'6h',NOW()-INTERVAL'1h'),

(bk07, tenant_a, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00007',
 svc_consult, staff_lerato, NULL,
 'Ayanda Ndaba','ayanda.n@ndaba.co.za','+27 71 700 0007',
 today,'11:00','11:20',20,
 'IN_PROGRESS',0.00,'ZAR','First visit - full assessment',NULL,
 true,NOW()-INTERVAL'45min',NULL,NULL,
 NULL,NULL,NULL,
 NOW()-INTERVAL'2d',NOW()-INTERVAL'45min'),

-- TODAY COMPLETED (drives revenue stat) ───────────────────────────────────────
(bk08, tenant_a, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00008',
 svc_consult, staff_lerato, NULL,
 'Rethabile Mosia','rethabile@mosia.co.za',NULL,
 today,'07:30','07:50',20,
 'COMPLETED',0.00,'ZAR',NULL,NULL,
 true,NOW()-INTERVAL'5h',NOW()-INTERVAL'4h',NULL,
 NULL,NULL,NULL,
 NOW()-INTERVAL'7d',NOW()-INTERVAL'4h'),

-- PENDING (3 - drives badge) ───────────────────────────────────────────────────
(bk09, tenant_a, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00009',
 svc_haircut, staff_thandi, NULL,
 'Lungelo Dube','lungelo@gmail.com','+27 83 900 0009',
 tomorrow,'09:00','09:45',45,
 'PENDING',350.00,'ZAR',NULL,NULL,
 false,NULL,NULL,NULL,
 NULL,NULL,NULL,
 NOW()-INTERVAL'1h',NOW()-INTERVAL'1h'),

(bk10, tenant_a, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00010',
 svc_massage, staff_sipho, NULL,
 'Nandi Vilakazi','nandi.v@vilakazi.net','+27 72 100 0010',
 next_week,'11:00','12:00',60,
 'PENDING',650.00,'ZAR','First time client - referred by Thabo',NULL,
 false,NULL,NULL,NULL,
 NULL,NULL,NULL,
 NOW()-INTERVAL'30min',NOW()-INTERVAL'30min'),

(bk11, tenant_a, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00011',
 svc_inspection, staff_marco, NULL,
 'Henk Pretorius','henk.p@propcheck.co.za','+27 12 110 0011',
 two_weeks_fwd,'10:00','13:00',180,
 'PENDING',2500.00,'ZAR','3-bed house Sandton - pre-purchase inspection',NULL,
 false,NULL,NULL,NULL,
 NULL,NULL,NULL,
 NOW()-INTERVAL'2h',NOW()-INTERVAL'2h'),

-- PAST COMPLETED (7) ──────────────────────────────────────────────────────────
(bk12, tenant_a, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00012',
 svc_colour, staff_thandi, NULL,
 'Amahle Zulu','amahle.z@gmail.com','+27 82 120 0012',
 yesterday,'10:00','12:00',120,
 'COMPLETED',950.00,'ZAR',NULL,NULL,
 true,NOW()-INTERVAL'2d',NOW()-INTERVAL'1d',NULL,
 NULL,NULL,NULL,
 NOW()-INTERVAL'5d',NOW()-INTERVAL'1d'),

(bk13, tenant_a, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00013',
 svc_mani_pedi, staff_lerato, NULL,
 'Sibusiso Kgaladi','sibusiso@kgaladi.co.za','+27 71 130 0013',
 yesterday,'14:00','15:30',90,
 'COMPLETED',520.00,'ZAR','Chrome finish requested',NULL,
 true,NOW()-INTERVAL'3d',NOW()-INTERVAL'1d',NULL,
 NULL,NULL,NULL,
 NOW()-INTERVAL'6d',NOW()-INTERVAL'1d'),

(bk14, tenant_a, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00014',
 svc_training, staff_sipho, NULL,
 'Dylan Botha','dylan.botha@fitness.co.za','+27 83 140 0014',
 two_ago,'07:00','08:00',60,
 'COMPLETED',400.00,'ZAR','Leg day focus - avoid high intensity',NULL,
 true,NOW()-INTERVAL'4d',NOW()-INTERVAL'2d',NULL,
 NULL,NULL,NULL,
 NOW()-INTERVAL'7d',NOW()-INTERVAL'2d'),

(bk15, tenant_a, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00015',
 svc_inspection, staff_marco, NULL,
 'Fatima Osman','fatima.o@osman.co.za','+27 11 150 0015',
 week_ago,'09:00','12:00',180,
 'COMPLETED',2500.00,'ZAR','2-bedroom flat in Rosebank',NULL,
 true,NOW()-INTERVAL'10d',NOW()-INTERVAL'7d',NULL,
 NULL,NULL,NULL,
 NOW()-INTERVAL'14d',NOW()-INTERVAL'7d'),

(bk16, tenant_a, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00016',
 svc_facial, staff_lerato, NULL,
 'Mmabatho Sefolo','mmabatho@gmail.com','+27 72 160 0016',
 week_ago+1,'11:00','12:00',60,
 'COMPLETED',480.00,'ZAR',NULL,NULL,
 true,NOW()-INTERVAL'9d',NOW()-INTERVAL'6d',NULL,
 NULL,NULL,NULL,
 NOW()-INTERVAL'13d',NOW()-INTERVAL'6d'),

(bk17, tenant_a, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00017',
 svc_haircut, staff_thandi, NULL,
 'Rorisang Tladi','rori@tladi.net','+27 82 170 0017',
 two_weeks,'08:00','08:45',45,
 'COMPLETED',350.00,'ZAR',NULL,NULL,
 true,NOW()-INTERVAL'16d',NOW()-INTERVAL'14d',NULL,
 NULL,NULL,NULL,
 NOW()-INTERVAL'20d',NOW()-INTERVAL'14d'),

-- Rescheduled booking — shows "rescheduled" badge in UI
(bk18, tenant_a, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00018',
 svc_massage, staff_sipho, NULL,
 'Tshepo Mahlangu','tshepo@mahlangu.co.za','+27 83 180 0018',
 month_ago,'15:00','16:00',60,
 'COMPLETED',650.00,'ZAR','Was rescheduled from original Monday slot',NULL,
 true,NOW()-INTERVAL'32d',NOW()-INTERVAL'30d',NULL,
 month_ago-3,'09:00',NOW()-INTERVAL'33d',
 NOW()-INTERVAL'40d',NOW()-INTERVAL'30d'),

-- CANCELLED (4) ───────────────────────────────────────────────────────────────
(bk19, tenant_a, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00019',
 svc_colour, staff_thandi, NULL,
 'Palesa Molete','palesa.m@molete.co.za','+27 71 190 0019',
 yesterday,'13:00','15:00',120,
 'CANCELLED',950.00,'ZAR',NULL,'Client called - family emergency',
 false,NOW()-INTERVAL'3d',NULL,NOW()-INTERVAL'1d',
 NULL,NULL,NULL,
 NOW()-INTERVAL'5d',NOW()-INTERVAL'1d'),

(bk20, tenant_a, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00020',
 svc_training, staff_sipho, NULL,
 'Innocent Mkhize','innocent@mkhize.net','+27 83 200 0020',
 week_ago+2,'07:00','08:00',60,
 'CANCELLED',400.00,'ZAR',NULL,NULL,
 true,NOW()-INTERVAL'8d',NULL,NOW()-INTERVAL'6d',
 NULL,NULL,NULL,
 NOW()-INTERVAL'12d',NOW()-INTERVAL'6d'),

(bk21, tenant_a, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00021',
 svc_haircut, staff_thandi, NULL,
 'Sello Mofokeng','sello@mofokeng.co.za','+27 82 210 0021',
 two_weeks-1,'10:00','10:45',45,
 'CANCELLED',350.00,'ZAR',NULL,'Load shedding affected equipment',
 true,NOW()-INTERVAL'17d',NULL,NOW()-INTERVAL'15d',
 NULL,NULL,NULL,
 NOW()-INTERVAL'20d',NOW()-INTERVAL'15d'),

(bk22, tenant_a, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00022',
 svc_facial, staff_lerato, NULL,
 'Charity Banda','charity@banda.co.za','+27 72 220 0022',
 month_ago+5,'14:00','15:00',60,
 'CANCELLED',480.00,'ZAR',NULL,'Client rescheduled to following week',
 false,NOW()-INTERVAL'28d',NULL,NOW()-INTERVAL'25d',
 NULL,NULL,NULL,
 NOW()-INTERVAL'35d',NOW()-INTERVAL'25d'),

-- NO_SHOW (3) ─────────────────────────────────────────────────────────────────
(bk23, tenant_a, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00023',
 svc_mani_pedi, staff_lerato, NULL,
 'Kelebogile Sithole','kelebo@gmail.com','+27 71 230 0023',
 week_ago+3,'09:00','10:30',90,
 'NO_SHOW',520.00,'ZAR',NULL,NULL,
 true,NOW()-INTERVAL'7d',NULL,NOW()-INTERVAL'4d',
 NULL,NULL,NULL,
 NOW()-INTERVAL'11d',NOW()-INTERVAL'4d'),

(bk24, tenant_a, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00024',
 svc_haircut, staff_thandi, NULL,
 'Unknown Client',NULL,'+27 82 240 0024',
 two_weeks-2,'11:00','11:45',45,
 'NO_SHOW',350.00,'ZAR','New client booked online',NULL,
 true,NOW()-INTERVAL'15d',NULL,NOW()-INTERVAL'16d',
 NULL,NULL,NULL,
 NOW()-INTERVAL'18d',NOW()-INTERVAL'16d'),

(bk25, tenant_a, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00025',
 svc_massage, staff_sipho, NULL,
 'Dineo Ramaphosa','dineo.r@ramaphosa.co.za','+27 83 250 0025',
 month_ago+10,'16:00','17:00',60,
 'NO_SHOW',650.00,'ZAR',NULL,NULL,
 true,NOW()-INTERVAL'22d',NULL,NOW()-INTERVAL'20d',
 NULL,NULL,NULL,
 NOW()-INTERVAL'28d',NOW()-INTERVAL'20d');


-- ── Tenant B isolation data ───────────────────────────────────────────────────
INSERT INTO booking_services
    (id, tenant_id, name, description, duration_minutes, price, currency,
     color, active, buffer_before_minutes, buffer_after_minutes,
     min_lead_time_minutes, max_advance_days, created_at, updated_at)
VALUES
(svc_b_cut, tenant_b, 'Beta Site Visit', 'Tenant B only - isolation test',
 30, 200.00, 'ZAR', '#DC2626', true, 0, 0, 0, 90, NOW(), NOW());

INSERT INTO booking_staff (id, tenant_id, name, email, phone, active, created_at, updated_at) VALUES
(staff_b_one, tenant_b, 'Beta Inspector', 'inspector@betaconstruction.co.za', '+27 11 999 0001', true, NOW(), NOW());

INSERT INTO booking_availability (id, tenant_id, staff_id, day_of_week, start_time, end_time, active) VALUES
(avb1, tenant_b, NULL, 1, '09:00', '17:00', true),
(avb2, tenant_b, NULL, 2, '09:00', '17:00', true),
(avb3, tenant_b, NULL, 3, '09:00', '17:00', true),
(avb4, tenant_b, NULL, 4, '09:00', '17:00', true),
(avb5, tenant_b, NULL, 5, '09:00', '17:00', true);

INSERT INTO booking_number_seq (tenant_id, year, last_seq)
VALUES (tenant_b, EXTRACT(YEAR FROM NOW())::INT, 2)
ON CONFLICT (tenant_id, year) DO UPDATE SET last_seq = GREATEST(booking_number_seq.last_seq, 2);

INSERT INTO bookings VALUES
(bkb1, tenant_b, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00001',
 svc_b_cut, staff_b_one, NULL,
 'Beta Construction Client A','client.a@betaconstruction.co.za','+27 11 001 0001',
 today,'10:00','10:30',30,
 'CONFIRMED',200.00,'ZAR','Tenant B isolation test - must not appear in Tenant A',NULL,
 true,NOW()-INTERVAL'1h',NULL,NULL,
 NULL,NULL,NULL,
 NOW()-INTERVAL'2d',NOW()-INTERVAL'1h'),

(bkb2, tenant_b, 'BK-'||EXTRACT(YEAR FROM NOW())::TEXT||'-00002',
 svc_b_cut, staff_b_one, NULL,
 'Beta Construction Client B','client.b@betaconstruction.co.za','+27 11 001 0002',
 yesterday,'11:00','11:30',30,
 'COMPLETED',200.00,'ZAR',NULL,NULL,
 true,NOW()-INTERVAL'3d',NOW()-INTERVAL'1d',NULL,
 NULL,NULL,NULL,
 NOW()-INTERVAL'5d',NOW()-INTERVAL'1d');

END $$;

COMMIT;