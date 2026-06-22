-- V85 — Rich clinic test data (corrected for exact schema)
-- address is JSONB, full_name is GENERATED (omit from INSERT)
-- practitioners have no email column in V19 — omitted
--
-- RUN:
--   docker cp V85__clinic_rich_test_data.sql handyflow-db:/tmp/V85.sql
--   docker exec -i handyflow-db psql -U handyflow -d handyflow -f /tmp/V85.sql

\set tenant '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f'

-- ── PRACTITIONERS ─────────────────────────────────────────────────────────────

INSERT INTO clinic_practitioners
    (id, tenant_id, first_name, last_name, specialty, hpcsa_number, practice_number, phone, active, created_at, updated_at)
VALUES
    ('a1000000-0000-0000-0000-000000000001', :'tenant', 'Sarah',  'Khumalo',      'General Practitioner', 'MP0123456', 'PR0001', '+27110001001', true, NOW(), NOW()),
    ('a1000000-0000-0000-0000-000000000002', :'tenant', 'James',  'van der Berg', 'Cardiologist',         'MP0234567', 'PR0002', '+27110001002', true, NOW(), NOW()),
    ('a1000000-0000-0000-0000-000000000003', :'tenant', 'Priya',  'Govender',     'Paediatrician',        'MP0345678', 'PR0003', '+27110001003', true, NOW(), NOW()),
    ('a1000000-0000-0000-0000-000000000004', :'tenant', 'Andile', 'Dlamini',      'Physiotherapist',      'MP0456789', 'PR0004', '+27110001004', true, NOW(), NOW()),
    ('a1000000-0000-0000-0000-000000000005', :'tenant', 'Sarah',  'Mokoena',      'General Practitioner', 'MP0567890', 'PR0005', '+27110001005', true, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ── PATIENTS ──────────────────────────────────────────────────────────────────
-- full_name is GENERATED ALWAYS — must not be included in INSERT
-- address is JSONB — must be valid JSON

INSERT INTO clinic_patients
    (id, tenant_id, first_name, last_name, id_number, date_of_birth, gender, blood_type,
     phone, email, address, account_type, active, created_at, updated_at)
VALUES
    ('b1000000-0000-0000-0000-000000000001', :'tenant', 'Jane',    'Dlamini',    '8601015800082', '1986-01-01', 'FEMALE', 'A+',  '+27831002000', 'jane.d@email.com',    '{"street":"12 Oak St","city":"Pretoria","province":"GP"}', 'PRINCIPAL',  true, NOW()-INTERVAL '2 years',  NOW()),
    ('b1000000-0000-0000-0000-000000000002', :'tenant', 'Sipho',   'Nkosi',      '7805125800083', '1978-05-12', 'MALE',   'O+',  '+27821003000', 'sipho.n@email.com',   '{"street":"5 Elm Ave","city":"Johannesburg","province":"GP"}', 'INDIVIDUAL', true, NOW()-INTERVAL '1 year',   NOW()),
    ('b1000000-0000-0000-0000-000000000003', :'tenant', 'Fatima',  'Moosa',      '9203204800081', '1992-03-20', 'FEMALE', 'B+',  '+27831004000', 'fatima.m@email.com',  '{"street":"8 Pine Rd","city":"Durban","province":"KZN"}',     'INDIVIDUAL', true, NOW()-INTERVAL '8 months', NOW()),
    ('b1000000-0000-0000-0000-000000000004', :'tenant', 'Themba',  'Zulu',       '6504034800080', '1965-04-03', 'MALE',   'AB+', '+27821005000', 'themba.z@email.com',  '{"street":"3 Maple Dr","city":"Pretoria","province":"GP"}',   'PRINCIPAL',  true, NOW()-INTERVAL '3 years',  NOW()),
    ('b1000000-0000-0000-0000-000000000005', :'tenant', 'Naledi',  'Zulu',       '9512154800082', '1995-12-15', 'FEMALE', 'AB+', '+27831006000', 'naledi.z@email.com',  '{"street":"3 Maple Dr","city":"Pretoria","province":"GP"}',   'DEPENDANT',  true, NOW()-INTERVAL '3 years',  NOW()),
    ('b1000000-0000-0000-0000-000000000006', :'tenant', 'Ruan',    'Zulu',       '0208154800083', '2002-08-15', 'MALE',   'A+',  '+27831007000', null,                  '{"street":"3 Maple Dr","city":"Pretoria","province":"GP"}',   'DEPENDANT',  true, NOW()-INTERVAL '3 years',  NOW()),
    ('b1000000-0000-0000-0000-000000000007', :'tenant', 'Dorothy', 'Dlamini',    '5507014800081', '1955-07-01', 'FEMALE', 'O-',  '+27821008000', 'dorothy.d@email.com', '{"street":"7 Birch Ln","city":"Sandton","province":"GP"}',    'INDIVIDUAL', true, NOW()-INTERVAL '5 years',  NOW()),
    ('b1000000-0000-0000-0000-000000000008', :'tenant', 'Kobus',   'van Wyk',    '6003034800080', '1960-03-03', 'MALE',   'B-',  '+27821009000', 'kobus.w@email.com',   '{"street":"22 Church St","city":"Pretoria","province":"GP"}', 'INDIVIDUAL', true, NOW()-INTERVAL '2 years',  NOW()),
    ('b1000000-0000-0000-0000-000000000009', :'tenant', 'Liam',    'Govender',   null,            '2019-03-15', 'MALE',   'A+',  '+27821010000', null,                  '{"street":"15 Rose St","city":"Durban","province":"KZN"}',    'DEPENDANT',  true, NOW()-INTERVAL '4 years',  NOW()),
    ('b1000000-0000-0000-0000-000000000010', :'tenant', 'Aisha',   'Govender',   null,            '2021-06-01', 'FEMALE', 'A+',  '+27821010000', null,                  '{"street":"15 Rose St","city":"Durban","province":"KZN"}',    'DEPENDANT',  true, NOW()-INTERVAL '2 years',  NOW()),
    ('b1000000-0000-0000-0000-000000000011', :'tenant', 'Preethi', 'Govender',   '8709174800083', '1987-09-17', 'FEMALE', 'A+',  '+27821010000', 'preethi.g@email.com', '{"street":"15 Rose St","city":"Durban","province":"KZN"}',    'PRINCIPAL',  true, NOW()-INTERVAL '4 years',  NOW()),
    ('b1000000-0000-0000-0000-000000000012', :'tenant', 'Marco',   'Rossouw',    '8811124800080', '1988-11-12', 'MALE',   'O+',  '+27821011000', 'marco.r@email.com',   '{"street":"9 Jacaranda Ave","city":"Pretoria","province":"GP"}','INDIVIDUAL',true, NOW()-INTERVAL '6 months', NOW()),
    ('b1000000-0000-0000-0000-000000000013', :'tenant', 'Zanele',  'Mokoena',    '9001264800081', '1990-01-26', 'FEMALE', 'B+',  '+27831012000', 'zanele.m@email.com',  '{"street":"4 Fern Rd","city":"Soweto","province":"GP"}',      'INDIVIDUAL', true, NOW()-INTERVAL '1 year',   NOW()),
    ('b1000000-0000-0000-0000-000000000014', :'tenant', 'Ahmed',   'Hassan',     '7706064800082', '1977-06-06', 'MALE',   'AB-', '+27821013000', 'ahmed.h@email.com',   '{"street":"18 Sultan St","city":"Lenasia","province":"GP"}',  'INDIVIDUAL', true, NOW()-INTERVAL '3 months', NOW()),
    ('b1000000-0000-0000-0000-000000000015', :'tenant', 'Cecilia', 'Pieterse',   '6209224800081', '1962-09-22', 'FEMALE', 'O+',  '+27821014000', 'cecilia.p@email.com', '{"street":"6 Voortrekker Rd","city":"Pretoria","province":"GP"}','INDIVIDUAL',true, NOW()-INTERVAL '1 month',  NOW())
ON CONFLICT (id) DO NOTHING;

-- Family links
UPDATE clinic_patients SET principal_id = 'b1000000-0000-0000-0000-000000000004', relationship = 'CHILD'  WHERE id = 'b1000000-0000-0000-0000-000000000005';
UPDATE clinic_patients SET principal_id = 'b1000000-0000-0000-0000-000000000004', relationship = 'CHILD'  WHERE id = 'b1000000-0000-0000-0000-000000000006';
UPDATE clinic_patients SET principal_id = 'b1000000-0000-0000-0000-000000000011', relationship = 'CHILD'  WHERE id = 'b1000000-0000-0000-0000-000000000009';
UPDATE clinic_patients SET principal_id = 'b1000000-0000-0000-0000-000000000011', relationship = 'CHILD'  WHERE id = 'b1000000-0000-0000-0000-000000000010';

-- ── MEDICAL AIDS ──────────────────────────────────────────────────────────────

INSERT INTO clinic_medical_aids
    (id, tenant_id, patient_id, scheme_name, plan_name, member_number, dependent_code, principal_member, active, created_at, updated_at)
VALUES
    (gen_random_uuid(), :'tenant', 'b1000000-0000-0000-0000-000000000001', 'Discovery Health',   'Comprehensive',  'DH-001-2024',  '00', 'Jane Dlamini',    true, NOW(), NOW()),
    (gen_random_uuid(), :'tenant', 'b1000000-0000-0000-0000-000000000002', 'Momentum Health',    'Ingwe',          'MOM-002-24',   '00', 'Sipho Nkosi',     true, NOW(), NOW()),
    (gen_random_uuid(), :'tenant', 'b1000000-0000-0000-0000-000000000003', 'Bonitas',            'BonEssential',   'BON-003-24',   '00', 'Fatima Moosa',    true, NOW(), NOW()),
    (gen_random_uuid(), :'tenant', 'b1000000-0000-0000-0000-000000000004', 'Fedhealth',          'FlexiFed 4',     'FED-004-24',   '00', 'Themba Zulu',     true, NOW(), NOW()),
    (gen_random_uuid(), :'tenant', 'b1000000-0000-0000-0000-000000000005', 'Fedhealth',          'FlexiFed 4',     'FED-004-24',   '01', 'Themba Zulu',     true, NOW(), NOW()),
    (gen_random_uuid(), :'tenant', 'b1000000-0000-0000-0000-000000000006', 'Fedhealth',          'FlexiFed 4',     'FED-004-24',   '02', 'Themba Zulu',     true, NOW(), NOW()),
    (gen_random_uuid(), :'tenant', 'b1000000-0000-0000-0000-000000000007', 'Bonitas',            'BonClassic',     'BON-007-24',   '00', 'Dorothy Dlamini', true, NOW(), NOW()),
    (gen_random_uuid(), :'tenant', 'b1000000-0000-0000-0000-000000000008', 'Medihelp',           'MedPlus',        'MED-008-24',   '00', 'Kobus van Wyk',   true, NOW(), NOW()),
    (gen_random_uuid(), :'tenant', 'b1000000-0000-0000-0000-000000000011', 'Discovery Health',   'Essential Saver','DH-011-24',    '00', 'Preethi Govender',true, NOW(), NOW()),
    (gen_random_uuid(), :'tenant', 'b1000000-0000-0000-0000-000000000009', 'Discovery Health',   'Essential Saver','DH-011-24',    '01', 'Preethi Govender',true, NOW(), NOW()),
    (gen_random_uuid(), :'tenant', 'b1000000-0000-0000-0000-000000000010', 'Discovery Health',   'Essential Saver','DH-011-24',    '02', 'Preethi Govender',true, NOW(), NOW()),
    (gen_random_uuid(), :'tenant', 'b1000000-0000-0000-0000-000000000012', 'Momentum Health',    'Custom',         'MOM-012-24',   '00', 'Marco Rossouw',   true, NOW(), NOW()),
    (gen_random_uuid(), :'tenant', 'b1000000-0000-0000-0000-000000000013', 'Gems',               'Emerald',        'GEM-013-24',   '00', 'Zanele Mokoena',  true, NOW(), NOW())
    -- Ahmed Hassan and Cecilia Pieterse are cash-pay: no medical aid record
ON CONFLICT DO NOTHING;

-- ── APPOINTMENTS ──────────────────────────────────────────────────────────────

INSERT INTO clinic_appointments
    (id, tenant_id, patient_id, practitioner_id, scheduled_at, duration_minutes,
     appointment_type, reason, status, created_at, updated_at)
VALUES
    -- Today completed
    ('c1000000-0000-0000-0000-000000000001', :'tenant', 'b1000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001', NOW()::date + TIME '08:30', 30, 'CONSULTATION', 'Annual wellness check',       'COMPLETED',   NOW(), NOW()),
    ('c1000000-0000-0000-0000-000000000002', :'tenant', 'b1000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000001', NOW()::date + TIME '09:00', 30, 'FOLLOW_UP',   'Hypertension follow-up',      'COMPLETED',   NOW(), NOW()),
    -- Today in progress / confirmed
    ('c1000000-0000-0000-0000-000000000003', :'tenant', 'b1000000-0000-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000005', NOW()::date + TIME '10:00', 30, 'CONSULTATION', 'Chest pain',                  'IN_PROGRESS', NOW(), NOW()),
    ('c1000000-0000-0000-0000-000000000004', :'tenant', 'b1000000-0000-0000-0000-000000000007', 'a1000000-0000-0000-0000-000000000002', NOW()::date + TIME '11:00', 45, 'CONSULTATION', 'Cardiac assessment',          'CONFIRMED',   NOW(), NOW()),
    ('c1000000-0000-0000-0000-000000000005', :'tenant', 'b1000000-0000-0000-0000-000000000015', 'a1000000-0000-0000-0000-000000000001', NOW()::date + TIME '14:00', 30, 'CONSULTATION', 'New patient — back pain',     'CONFIRMED',   NOW(), NOW()),
    ('c1000000-0000-0000-0000-000000000006', :'tenant', 'b1000000-0000-0000-0000-000000000014', 'a1000000-0000-0000-0000-000000000001', NOW()::date + TIME '14:30', 30, 'CONSULTATION', 'Diabetes review',             'CONFIRMED',   NOW(), NOW()),
    ('c1000000-0000-0000-0000-000000000007', :'tenant', 'b1000000-0000-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000002', NOW()::date + TIME '16:00', 45, 'CONSULTATION', 'Cardiac review',              'CONFIRMED',   NOW(), NOW()),
    -- This week
    ('c1000000-0000-0000-0000-000000000008', :'tenant', 'b1000000-0000-0000-0000-000000000012', 'a1000000-0000-0000-0000-000000000004', NOW()::date + INTERVAL '1 day' + TIME '09:00', 60, 'PROCEDURE',    'Lower back physio',       'CONFIRMED', NOW(), NOW()),
    ('c1000000-0000-0000-0000-000000000009', :'tenant', 'b1000000-0000-0000-0000-000000000009', 'a1000000-0000-0000-0000-000000000003', NOW()::date + INTERVAL '1 day' + TIME '10:00', 30, 'CONSULTATION', 'Child wellness 7yr',      'CONFIRMED', NOW(), NOW()),
    ('c1000000-0000-0000-0000-000000000010', :'tenant', 'b1000000-0000-0000-0000-000000000004', 'a1000000-0000-0000-0000-000000000002', NOW()::date + INTERVAL '2 days' + TIME '08:00', 60, 'CONSULTATION','Echo follow-up',          'SCHEDULED', NOW(), NOW()),
    ('c1000000-0000-0000-0000-000000000011', :'tenant', 'b1000000-0000-0000-0000-000000000013', 'a1000000-0000-0000-0000-000000000004', NOW()::date + INTERVAL '2 days' + TIME '11:00', 60, 'PROCEDURE',  'Shoulder physiotherapy',  'SCHEDULED', NOW(), NOW()),
    -- Past history
    ('c1000000-0000-0000-0000-000000000012', :'tenant', 'b1000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000001', NOW()-INTERVAL '14 days',  30, 'CONSULTATION', 'Hypertension poorly controlled', 'COMPLETED', NOW()-INTERVAL '14 days', NOW()),
    ('c1000000-0000-0000-0000-000000000013', :'tenant', 'b1000000-0000-0000-0000-000000000007', 'a1000000-0000-0000-0000-000000000002', NOW()-INTERVAL '30 days',  45, 'CONSULTATION', 'Post-op cardiac check',          'COMPLETED', NOW()-INTERVAL '30 days', NOW()),
    ('c1000000-0000-0000-0000-000000000014', :'tenant', 'b1000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001', NOW()-INTERVAL '90 days',  30, 'FOLLOW_UP',   'Blood results review',           'COMPLETED', NOW()-INTERVAL '90 days', NOW()),
    ('c1000000-0000-0000-0000-000000000015', :'tenant', 'b1000000-0000-0000-0000-000000000008', 'a1000000-0000-0000-0000-000000000002', NOW()-INTERVAL '45 days',  45, 'CONSULTATION', 'ECG and stress test',            'COMPLETED', NOW()-INTERVAL '45 days', NOW()),
    -- No-show and cancellation
    ('c1000000-0000-0000-0000-000000000016', :'tenant', 'b1000000-0000-0000-0000-000000000014', 'a1000000-0000-0000-0000-000000000001', NOW()-INTERVAL '7 days',   30, 'CONSULTATION', 'Diabetes review',                'NO_SHOW',   NOW()-INTERVAL '7 days',  NOW()),
    ('c1000000-0000-0000-0000-000000000017', :'tenant', 'b1000000-0000-0000-0000-000000000005', 'a1000000-0000-0000-0000-000000000003', NOW()-INTERVAL '3 days',   30, 'CONSULTATION', 'Flu symptoms',                   'CANCELLED', NOW()-INTERVAL '3 days',  NOW())
ON CONFLICT (id) DO NOTHING;

-- ── CONSULTATIONS ─────────────────────────────────────────────────────────────

INSERT INTO clinic_consultations
    (id, tenant_id, patient_id, appointment_id, practitioner_id, consulted_at,
     weight_kg, height_cm, blood_pressure, pulse_bpm, temperature_c, oxygen_sat_pct,
     chief_complaint, history, examination, diagnosis, icd10_codes, treatment_plan,
     follow_up_days, billed, created_at, updated_at)
VALUES
    ('d1000000-0000-0000-0000-000000000001', :'tenant',
     'b1000000-0000-0000-0000-000000000001', 'c1000000-0000-0000-0000-000000000001',
     'a1000000-0000-0000-0000-000000000001', NOW()-INTERVAL '2 hours',
     68.5, 165.0, '118/76', 72, 36.6, 98.0,
     'Annual wellness check — no acute complaints',
     'Known hypothyroid on Euthyrox 50mcg. Fatigue occasionally. No new symptoms.',
     'Well-appearing female. BP normal. Thyroid not enlarged. Abdomen soft.',
     'Hypothyroidism well controlled. Iron deficiency anaemia.',
     ARRAY['E03.9','D50.9'],
     'Continue Euthyrox. Iron supplementation 3 months. FBC repeat in 3 months.',
     90, true, NOW()-INTERVAL '2 hours', NOW()),

    ('d1000000-0000-0000-0000-000000000002', :'tenant',
     'b1000000-0000-0000-0000-000000000002', 'c1000000-0000-0000-0000-000000000002',
     'a1000000-0000-0000-0000-000000000001', NOW()-INTERVAL '1 hour',
     88.0, 178.0, '148/92', 80, 36.8, 97.0,
     'Hypertension follow-up — BP still elevated',
     'Known hypertensive since 2019. On Amlodipine 5mg + Perindopril 4mg. Compliant.',
     'BP 148/92. Mild peripheral oedema bilateral. No chest pain or SOB.',
     'Hypertension uncontrolled. Investigate secondary cause.',
     ARRAY['I10'],
     'Increase Amlodipine to 10mg. Add HCTZ 12.5mg. U&E + renal function.',
     30, true, NOW()-INTERVAL '1 hour', NOW()),

    ('d1000000-0000-0000-0000-000000000003', :'tenant',
     'b1000000-0000-0000-0000-000000000007', 'c1000000-0000-0000-0000-000000000013',
     'a1000000-0000-0000-0000-000000000002', NOW()-INTERVAL '30 days',
     72.0, 160.0, '138/86', 68, 36.5, 96.0,
     'Post-CABG 4-week check',
     'CABG x3 performed 4 weeks ago. Wound healing well. Mild SOB on exertion.',
     'Sternotomy wound clean. Bilateral crepitations lower zones. HR 68 regular.',
     'Post-CABG recovery. Mild compensated heart failure.',
     ARRAY['I50.9','Z95.1'],
     'Continue Furosemide, Bisoprolol, Aspirin, Atorvastatin. Cardiac rehab referral.',
     30, true, NOW()-INTERVAL '30 days', NOW()),

    ('d1000000-0000-0000-0000-000000000004', :'tenant',
     'b1000000-0000-0000-0000-000000000002', 'c1000000-0000-0000-0000-000000000012',
     'a1000000-0000-0000-0000-000000000001', NOW()-INTERVAL '14 days',
     87.5, 178.0, '152/96', 84, 36.9, 97.0,
     'Hypertension poorly controlled — headaches',
     'Missing doses occasionally. Work stress. On Amlodipine 5mg only at this point.',
     'BP very elevated. Fundoscopy grade 2 hypertensive retinopathy.',
     'Hypertension poorly controlled. Grade 2 hypertensive retinopathy.',
     ARRAY['I10','H35.0'],
     'Counselled on adherence. Added Perindopril 4mg. Ophthalmology referral.',
     14, true, NOW()-INTERVAL '14 days', NOW()),

    ('d1000000-0000-0000-0000-000000000005', :'tenant',
     'b1000000-0000-0000-0000-000000000008', 'c1000000-0000-0000-0000-000000000015',
     'a1000000-0000-0000-0000-000000000002', NOW()-INTERVAL '45 days',
     95.0, 182.0, '142/88', 76, 36.7, 98.0,
     'Exertional chest pain — rule out ischaemia',
     'Former smoker. Family history IHD. Chest tightness on stairs for 3 months.',
     'ECG: ST changes V4-V6. Stress test positive at 85% MHRR.',
     'Coronary artery disease — likely significant. Urgent catheterisation.',
     ARRAY['I25.1'],
     'Urgent angiogram. Start Aspirin 150mg, Isosorbide mononitrate. Urgent cardiology.',
     7, false, NOW()-INTERVAL '45 days', NOW()),

    ('d1000000-0000-0000-0000-000000000006', :'tenant',
     'b1000000-0000-0000-0000-000000000001', 'c1000000-0000-0000-0000-000000000014',
     'a1000000-0000-0000-0000-000000000001', NOW()-INTERVAL '90 days',
     67.0, 165.0, '115/74', 70, 36.5, 99.0,
     'Blood results review — iron levels low',
     'TSH 3.2 normal. Ferritin 8 very low. Fatigue persisting for months.',
     'Pallor of conjunctivae. Otherwise well.',
     'Iron deficiency anaemia. Hypothyroidism stable.',
     ARRAY['D50.9','E03.9'],
     'Ferrograd-C 1 daily x3 months. Repeat FBC in 3 months.',
     90, false, NOW()-INTERVAL '90 days', NOW())
ON CONFLICT (id) DO NOTHING;

-- ── PRESCRIPTIONS ─────────────────────────────────────────────────────────────

INSERT INTO clinic_prescriptions
    (id, tenant_id, consultation_id, patient_id, practitioner_id,
     medication_name, dosage, frequency, duration, quantity, repeats,
     instructions, prescribed_at, created_at, updated_at)
VALUES
    (gen_random_uuid(), :'tenant', 'd1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001',
     'Euthyrox 50mcg', '50mcg', 'Once daily morning', 'Ongoing', 30, 5, 'Take on empty stomach 30min before food', NOW()-INTERVAL '2 hours', NOW(), NOW()),
    (gen_random_uuid(), :'tenant', 'd1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001',
     'Ferrograd-C', '1 tablet', 'Once daily with food', '3 months', 90, 2, 'Take with food to reduce nausea. May cause dark stools.', NOW()-INTERVAL '2 hours', NOW(), NOW()),
    (gen_random_uuid(), :'tenant', 'd1000000-0000-0000-0000-000000000002', 'b1000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000001',
     'Amlodipine 10mg', '10mg', 'Once daily', 'Ongoing', 30, 5, 'May cause ankle swelling — report if severe', NOW()-INTERVAL '1 hour', NOW(), NOW()),
    (gen_random_uuid(), :'tenant', 'd1000000-0000-0000-0000-000000000002', 'b1000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000001',
     'Perindopril 4mg', '4mg', 'Once daily', 'Ongoing', 30, 5, 'Dry cough is a known side effect — report to doctor', NOW()-INTERVAL '1 hour', NOW(), NOW()),
    (gen_random_uuid(), :'tenant', 'd1000000-0000-0000-0000-000000000002', 'b1000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000001',
     'Hydrochlorothiazide 12.5mg', '12.5mg', 'Once daily morning', 'Ongoing', 30, 5, 'Take in morning — causes increased urination', NOW()-INTERVAL '1 hour', NOW(), NOW()),
    (gen_random_uuid(), :'tenant', 'd1000000-0000-0000-0000-000000000003', 'b1000000-0000-0000-0000-000000000007', 'a1000000-0000-0000-0000-000000000002',
     'Bisoprolol 5mg', '5mg', 'Once daily', 'Ongoing', 30, 5, 'Do not stop suddenly — taper under supervision', NOW()-INTERVAL '30 days', NOW(), NOW()),
    (gen_random_uuid(), :'tenant', 'd1000000-0000-0000-0000-000000000003', 'b1000000-0000-0000-0000-000000000007', 'a1000000-0000-0000-0000-000000000002',
     'Furosemide 40mg', '40mg', 'Once daily morning', 'Ongoing', 30, 5, 'Weigh daily — report gain >2kg in 2 days', NOW()-INTERVAL '30 days', NOW(), NOW()),
    (gen_random_uuid(), :'tenant', 'd1000000-0000-0000-0000-000000000003', 'b1000000-0000-0000-0000-000000000007', 'a1000000-0000-0000-0000-000000000002',
     'Atorvastatin 40mg', '40mg', 'Once daily evening', 'Ongoing', 30, 11, 'Avoid grapefruit. Report muscle pain immediately.', NOW()-INTERVAL '30 days', NOW(), NOW()),
    (gen_random_uuid(), :'tenant', 'd1000000-0000-0000-0000-000000000003', 'b1000000-0000-0000-0000-000000000007', 'a1000000-0000-0000-0000-000000000002',
     'Aspirin 75mg', '75mg', 'Once daily with food', 'Ongoing', 30, 11, 'Take with food to protect stomach lining', NOW()-INTERVAL '30 days', NOW(), NOW()),
    (gen_random_uuid(), :'tenant', 'd1000000-0000-0000-0000-000000000005', 'b1000000-0000-0000-0000-000000000008', 'a1000000-0000-0000-0000-000000000002',
     'Aspirin 150mg', '150mg', 'Once daily with food', 'Ongoing', 30, 5, 'Loading dose — do not stop without cardiology consult', NOW()-INTERVAL '45 days', NOW(), NOW()),
    (gen_random_uuid(), :'tenant', 'd1000000-0000-0000-0000-000000000005', 'b1000000-0000-0000-0000-000000000008', 'a1000000-0000-0000-0000-000000000002',
     'Isosorbide Mononitrate 20mg', '20mg', 'Twice daily', 'Until procedure', 60, 1, 'May cause headache — take with paracetamol if needed', NOW()-INTERVAL '45 days', NOW(), NOW())
ON CONFLICT DO NOTHING;

-- ── CLAIMS ────────────────────────────────────────────────────────────────────

INSERT INTO clinic_claims
    (id, tenant_id, consultation_id, patient_id, practitioner_id, status,
     scheme_name, member_number, dependent_code, gross_amount, scheme_portion, patient_portion,
     submitted_at, reference_number, created_at, updated_at)
VALUES
    -- PAID — Jane Dlamini, Discovery
    ('e1000000-0000-0000-0000-000000000001', :'tenant',
     'd1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001',
     'PAID', 'Discovery Health', 'DH-001-2024', '00', 645.00, 580.50, 64.50,
     NOW()-INTERVAL '2 days', 'DH-2026-001234', NOW()-INTERVAL '2 days', NOW()),
    -- SUBMITTED — Sipho Nkosi, Momentum
    ('e1000000-0000-0000-0000-000000000002', :'tenant',
     'd1000000-0000-0000-0000-000000000002', 'b1000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000001',
     'SUBMITTED', 'Momentum Health', 'MOM-002-24', '00', 645.00, 0, 645.00,
     NOW()-INTERVAL '1 day', 'MOM-2026-005678', NOW()-INTERVAL '1 day', NOW()),
    -- PAID — Dorothy Dlamini, Bonitas, specialist rates
    ('e1000000-0000-0000-0000-000000000003', :'tenant',
     'd1000000-0000-0000-0000-000000000003', 'b1000000-0000-0000-0000-000000000007', 'a1000000-0000-0000-0000-000000000002',
     'PAID', 'Bonitas', 'BON-007-24', '00', 1040.00, 936.00, 104.00,
     NOW()-INTERVAL '25 days', 'BON-2026-009012', NOW()-INTERVAL '25 days', NOW()),
    -- DRAFT — Sipho Nkosi 14-day visit
    ('e1000000-0000-0000-0000-000000000004', :'tenant',
     'd1000000-0000-0000-0000-000000000004', 'b1000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000001',
     'DRAFT', 'Momentum Health', 'MOM-002-24', '00', 645.00, 0, 645.00,
     null, null, NOW()-INTERVAL '14 days', NOW()),
    -- REJECTED — Kobus, Medihelp, missing ICD-10 on procedure line
    ('e1000000-0000-0000-0000-000000000005', :'tenant',
     'd1000000-0000-0000-0000-000000000005', 'b1000000-0000-0000-0000-000000000008', 'a1000000-0000-0000-0000-000000000002',
     'REJECTED', 'Medihelp', 'MED-008-24', '00', 1350.00, 0, 1350.00,
     NOW()-INTERVAL '40 days', 'MED-REJ-2026-001',
     NOW()-INTERVAL '40 days', NOW())
ON CONFLICT (id) DO NOTHING;

UPDATE clinic_claims SET rejection_reason = 'Procedure line 2 missing valid ICD-10 code. Resubmit with corrected claim.'
WHERE id = 'e1000000-0000-0000-0000-000000000005';

INSERT INTO clinic_claim_lines
    (id, claim_id, line_type, tariff_code, icd10_code, description, quantity, unit_price, gross_amount, scheme_portion, patient_portion, sort_order, created_at)
VALUES
    -- Claim 1 lines
    (gen_random_uuid(), 'e1000000-0000-0000-0000-000000000001', 'CONSULTATION', '0191', 'E03.9', 'Consultation — established patient, intermediate', 1, 520.00, 520.00, 468.00,  52.00, 0,  NOW()),
    (gen_random_uuid(), 'e1000000-0000-0000-0000-000000000001', 'MEDICINE',     null,   'D50.9', 'Ferrograd-C x90',                                  90, 1.39,  125.10, 112.59,  12.51, 10, NOW()),
    -- Claim 2 lines
    (gen_random_uuid(), 'e1000000-0000-0000-0000-000000000002', 'CONSULTATION', '0191', 'I10',   'Consultation — established patient, intermediate', 1, 520.00, 520.00, 0,       520.00, 0, NOW()),
    (gen_random_uuid(), 'e1000000-0000-0000-0000-000000000002', 'MEDICINE',     null,   'I10',   'Amlodipine 10mg x30',                              30, 2.50,  75.00,  0,        75.00, 10,NOW()),
    (gen_random_uuid(), 'e1000000-0000-0000-0000-000000000002', 'MEDICINE',     null,   'I10',   'Perindopril 4mg x30',                              30, 1.67,  50.10,  0,        50.10, 20,NOW()),
    -- Claim 3 lines (specialist)
    (gen_random_uuid(), 'e1000000-0000-0000-0000-000000000003', 'CONSULTATION', '0191', 'I50.9', 'Specialist consultation — established patient',    1, 780.00, 780.00, 702.00, 78.00,  0,  NOW()),
    (gen_random_uuid(), 'e1000000-0000-0000-0000-000000000003', 'PROCEDURE',    '0301', 'I50.9', 'ECG 12-lead interpretation',                       1, 260.00, 260.00, 234.00, 26.00,  10, NOW()),
    -- Claim 4 lines (draft)
    (gen_random_uuid(), 'e1000000-0000-0000-0000-000000000004', 'CONSULTATION', '0191', 'I10',   'Consultation — established patient, intermediate', 1, 520.00, 520.00, 0,       520.00, 0, NOW()),
    (gen_random_uuid(), 'e1000000-0000-0000-0000-000000000004', 'PROCEDURE',    '0115', 'I10',   'Injection IM — Furosemide 40mg stat',              1, 85.00,  85.00,  0,        85.00, 10,NOW()),
    (gen_random_uuid(), 'e1000000-0000-0000-0000-000000000004', 'MEDICINE',     null,   'I10',   'Hydrochlorothiazide 12.5mg x30',                   30, 1.33,  39.90,  0,        39.90, 20,NOW()),
    -- Claim 5 lines (rejected — procedure missing ICD-10)
    (gen_random_uuid(), 'e1000000-0000-0000-0000-000000000005', 'CONSULTATION', '0191', 'I25.1', 'Specialist consultation — new patient',            1, 780.00, 780.00, 0,       780.00, 0, NOW()),
    (gen_random_uuid(), 'e1000000-0000-0000-0000-000000000005', 'PROCEDURE',    '4116', null,    'ECG and stress test',                              1, 570.00, 570.00, 0,       570.00, 10,NOW())
ON CONFLICT DO NOTHING;

-- ── LAB RESULTS ───────────────────────────────────────────────────────────────

INSERT INTO clinic_lab_results
    (id, tenant_id, patient_id, consultation_id, source, lab_reference, received_at,
     pdf_filename, status, patient_name_raw, interpretation, notified, created_at, updated_at)
VALUES
    (gen_random_uuid(), :'tenant', 'b1000000-0000-0000-0000-000000000001', 'd1000000-0000-0000-0000-000000000006',
     'AMPATH', 'AMP-2026-10041', NOW()-INTERVAL '85 days', 'Dlamini_J_FBC_Mar2026.pdf', 'FILED', 'DLAMINI J',
     'FBC shows microcytic hypochromic anaemia consistent with iron deficiency. Hb 9.2 g/dL (low), MCV 68 fL (low), Ferritin 8 µg/L (very low). Iron supplementation recommended.',
     true, NOW()-INTERVAL '85 days', NOW()),
    (gen_random_uuid(), :'tenant', 'b1000000-0000-0000-0000-000000000002', null,
     'LANCET', 'LAN-2026-20187', NOW()-INTERVAL '10 days', 'Nkosi_S_UandE_Jun2026.pdf', 'REVIEWED', 'NKOSI S',
     'U&E shows borderline hypokalaemia (K+ 3.2 mmol/L) consistent with diuretic use. Creatinine 118 µmol/L — mild renal impairment. Monitor closely on HCTZ.',
     true, NOW()-INTERVAL '10 days', NOW()),
    (gen_random_uuid(), :'tenant', 'b1000000-0000-0000-0000-000000000007', 'd1000000-0000-0000-0000-000000000003',
     'AMPATH', 'AMP-2026-30092', NOW()-INTERVAL '28 days', 'Dlamini_D_BNP_May2026.pdf', 'FILED', 'DLAMINI DOROTHY',
     'BNP markedly elevated at 890 pg/mL (normal <100), confirming significant cardiac stress. Pro-BNP 4200. Consistent with decompensated heart failure post-CABG.',
     true, NOW()-INTERVAL '28 days', NOW()),
    (gen_random_uuid(), :'tenant', 'b1000000-0000-0000-0000-000000000008', null,
     'PATHCARE', 'PAT-2026-40053', NOW()-INTERVAL '1 day', 'vanWyk_K_Lipogram_Jun2026.pdf', 'UNREVIEWED', 'VAN WYK K',
     null, false, NOW()-INTERVAL '1 day', NOW()),
    (gen_random_uuid(), :'tenant', 'b1000000-0000-0000-0000-000000000001', null,
     'AMPATH', 'AMP-2026-50219', NOW()-INTERVAL '2 hours', 'Dlamini_J_TSH_Jun2026.pdf', 'UNREVIEWED', 'DLAMINI J',
     null, false, NOW()-INTERVAL '2 hours', NOW())
ON CONFLICT DO NOTHING;

-- ── LAST VISIT / BILLED FLAGS ─────────────────────────────────────────────────

UPDATE clinic_patients SET last_visit_at = NOW()-INTERVAL '2 hours'  WHERE id = 'b1000000-0000-0000-0000-000000000001';
UPDATE clinic_patients SET last_visit_at = NOW()-INTERVAL '1 hour'   WHERE id = 'b1000000-0000-0000-0000-000000000002';
UPDATE clinic_patients SET last_visit_at = NOW()-INTERVAL '30 days'  WHERE id = 'b1000000-0000-0000-0000-000000000007';
UPDATE clinic_patients SET last_visit_at = NOW()-INTERVAL '45 days'  WHERE id = 'b1000000-0000-0000-0000-000000000008';

UPDATE clinic_consultations SET billed = true  WHERE id IN ('d1000000-0000-0000-0000-000000000001','d1000000-0000-0000-0000-000000000002','d1000000-0000-0000-0000-000000000003','d1000000-0000-0000-0000-000000000004');
UPDATE clinic_consultations SET billed = false WHERE id IN ('d1000000-0000-0000-0000-000000000005','d1000000-0000-0000-0000-000000000006');