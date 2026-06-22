-- ============================================================
-- V82 — Clinic QA Test Data
-- Tenant: Zeta Earthmoving (9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f)
-- Covers: patients (individual + family), practitioners, appointments,
--         consultations, prescriptions, claims, lab results
-- Run via: docker exec -i handyflow-db psql -U handyflow -d handyflow -f /tmp/V82.sql
-- ============================================================

DO $$
DECLARE
  tid         UUID := '9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f';

  -- Practitioners (already seeded — reference by lookup)
  dr_khumalo  UUID;
  dr_govender UUID;
  dr_mokoena  UUID;
  dr_berg     UUID;

  -- Individual patients
  p_sipho     UUID := gen_random_uuid();  -- existing chronic patient
  p_fatima    UUID := gen_random_uuid();  -- hypertension + diabetes
  p_liam      UUID := gen_random_uuid();  -- child, 7 yrs

  -- Family account: Botha family
  p_jane      UUID := gen_random_uuid();  -- principal
  p_tom       UUID := gen_random_uuid();  -- spouse
  p_alex      UUID := gen_random_uuid();  -- child, 5 yrs
  p_gran      UUID := gen_random_uuid();  -- grandparent

  -- Appointments
  a1 UUID := gen_random_uuid();
  a2 UUID := gen_random_uuid();
  a3 UUID := gen_random_uuid();
  a4 UUID := gen_random_uuid();
  a5 UUID := gen_random_uuid();
  a6 UUID := gen_random_uuid();

  -- Consultations
  c1 UUID := gen_random_uuid();
  c2 UUID := gen_random_uuid();
  c3 UUID := gen_random_uuid();
  c4 UUID := gen_random_uuid();

  -- Prescriptions
  rx1 UUID := gen_random_uuid();
  rx2 UUID := gen_random_uuid();
  rx3 UUID := gen_random_uuid();
  rx4 UUID := gen_random_uuid();
  rx5 UUID := gen_random_uuid();

  -- Claims
  cl1 UUID := gen_random_uuid();
  cl2 UUID := gen_random_uuid();

  -- Claim lines
  cll1 UUID := gen_random_uuid();
  cll2 UUID := gen_random_uuid();
  cll3 UUID := gen_random_uuid();
  cll4 UUID := gen_random_uuid();

  -- Lab results
  lr1 UUID := gen_random_uuid();
  lr2 UUID := gen_random_uuid();

BEGIN

  -- ── Resolve practitioners by name ──────────────────────────────────────────
  SELECT id INTO dr_khumalo  FROM clinic_practitioners WHERE tenant_id = tid AND last_name = 'Khumalo'  LIMIT 1;
  SELECT id INTO dr_govender FROM clinic_practitioners WHERE tenant_id = tid AND last_name = 'Govender' LIMIT 1;
  SELECT id INTO dr_mokoena  FROM clinic_practitioners WHERE tenant_id = tid AND last_name = 'Mokoena'  LIMIT 1;
  SELECT id INTO dr_berg     FROM clinic_practitioners WHERE tenant_id = tid AND last_name ILIKE '%Berg%' LIMIT 1;

  -- ── INDIVIDUAL PATIENTS ────────────────────────────────────────────────────

  -- Sipho Nkosi — 62yo male, chronic hypertension + diabetes (already in DB as test data)
  -- Skip if already exists
  INSERT INTO clinic_patients (
    id, tenant_id, first_name, last_name, id_number, date_of_birth, gender,
    phone, email, blood_type, allergies, chronic_conditions,
    emergency_contact_name, emergency_contact_phone,
    account_type, active, created_at, updated_at,
    last_visit_at
  ) VALUES (
    p_sipho, tid, 'Sipho', 'Nkosi', '6405037113086', '1964-05-03', 'MALE',
    '+27 82 111 2233', 'sipho.nkosi@gmail.com', 'B+',
    ARRAY[]::text[],
    ARRAY['Type 2 diabetes', 'Hypertension'],
    'Nomsa Nkosi', '+27 82 444 5566',
    'INDIVIDUAL', true, NOW() - INTERVAL '2 years', NOW(),
    NOW() - INTERVAL '14 days'
  ) ON CONFLICT DO NOTHING;

  -- Fatima Moosa — 74yo female, hypertension + high cholesterol
  INSERT INTO clinic_patients (
    id, tenant_id, first_name, last_name, id_number, date_of_birth, gender,
    phone, email, blood_type, allergies, chronic_conditions,
    emergency_contact_name, emergency_contact_phone,
    account_type, active, created_at, updated_at, last_visit_at
  ) VALUES (
    p_fatima, tid, 'Fatima', 'Moosa', '5109305211082', '1951-09-30', 'FEMALE',
    '+27 73 222 3344', 'fatima.moosa@outlook.com', 'AB+',
    ARRAY['Penicillin', 'Sulfa drugs'],
    ARRAY['Hypertension', 'Hypercholesterolaemia', 'Osteoarthritis'],
    'Ahmed Moosa', '+27 73 999 0011',
    'INDIVIDUAL', true, NOW() - INTERVAL '3 years', NOW(),
    NOW() - INTERVAL '7 days'
  ) ON CONFLICT DO NOTHING;

  -- Liam Botha — 7yo male, child patient (individual account, NOT part of the family below)
  INSERT INTO clinic_patients (
    id, tenant_id, first_name, last_name, id_number, date_of_birth, gender,
    phone, blood_type, allergies, chronic_conditions,
    emergency_contact_name, emergency_contact_phone,
    account_type, active, created_at, updated_at
  ) VALUES (
    p_liam, tid, 'Liam', 'Botha', '2019015026088', '2019-01-15', 'MALE',
    '+27 72 666 7788', 'O+',
    ARRAY['Peanuts'],
    ARRAY[]::text[],
    'Kaili Botha', '+27 72 666 7788',
    'INDIVIDUAL', true, NOW() - INTERVAL '1 year', NOW()
  ) ON CONFLICT DO NOTHING;

  -- ── FAMILY ACCOUNT: The Dlamini family ────────────────────────────────────
  -- Jane Dlamini — 38yo female, PRINCIPAL
  INSERT INTO clinic_patients (
    id, tenant_id, first_name, last_name, id_number, date_of_birth, gender,
    phone, email, blood_type, allergies, chronic_conditions,
    emergency_contact_name, emergency_contact_phone,
    account_type, active, created_at, updated_at
  ) VALUES (
    p_jane, tid, 'Jane', 'Dlamini', '8601015826087', '1986-01-01', 'FEMALE',
    '+27 83 100 2000', 'jane.dlamini@gmail.com', 'A+',
    ARRAY[]::text[],
    ARRAY[]::text[],
    'Thomas Dlamini', '+27 83 100 2001',
    'PRINCIPAL', true, NOW() - INTERVAL '6 months', NOW()
  ) ON CONFLICT DO NOTHING;

  -- Thomas Dlamini — 40yo male, SPOUSE
  INSERT INTO clinic_patients (
    id, tenant_id, first_name, last_name, id_number, date_of_birth, gender,
    phone, email, blood_type, allergies, chronic_conditions,
    emergency_contact_name, emergency_contact_phone,
    account_type, principal_id, relationship, active, created_at, updated_at
  ) VALUES (
    p_tom, tid, 'Thomas', 'Dlamini', '8401015826086', '1984-01-01', 'MALE',
    '+27 83 100 2001', 'thomas.dlamini@gmail.com', 'O+',
    ARRAY[]::text[],
    ARRAY['Aspirin'],
    'Jane Dlamini', '+27 83 100 2000',
    'DEPENDANT', p_jane, 'SPOUSE', true, NOW() - INTERVAL '6 months', NOW()
  ) ON CONFLICT DO NOTHING;

  -- Alex Dlamini — 5yo child
  INSERT INTO clinic_patients (
    id, tenant_id, first_name, last_name, date_of_birth, gender,
    blood_type, allergies, chronic_conditions,
    emergency_contact_name, emergency_contact_phone,
    account_type, principal_id, relationship, active, created_at, updated_at
  ) VALUES (
    p_alex, tid, 'Alex', 'Dlamini', '2021-03-15', 'FEMALE',
    'A+', ARRAY[]::text[], ARRAY[]::text[],
    'Jane Dlamini', '+27 83 100 2000',
    'DEPENDANT', p_jane, 'CHILD', true, NOW() - INTERVAL '3 months', NOW()
  ) ON CONFLICT DO NOTHING;

  -- Grandma Dlamini — 68yo, GRANDPARENT
  INSERT INTO clinic_patients (
    id, tenant_id, first_name, last_name, date_of_birth, gender,
    phone, blood_type, allergies, chronic_conditions,
    emergency_contact_name, emergency_contact_phone,
    account_type, principal_id, relationship, active, created_at, updated_at
  ) VALUES (
    p_gran, tid, 'Dorothy', 'Dlamini', '1957-06-10', 'FEMALE',
    '+27 83 100 2002', 'B+',
    ARRAY[]::text[],
    ARRAY['Hypertension', 'Type 2 diabetes'],
    'Jane Dlamini', '+27 83 100 2000',
    'DEPENDANT', p_jane, 'GRANDPARENT', true, NOW() - INTERVAL '2 months', NOW()
  ) ON CONFLICT DO NOTHING;

  -- ── APPOINTMENTS ───────────────────────────────────────────────────────────

  -- Sipho — upcoming consultation (tomorrow 09:00)
  INSERT INTO clinic_appointments (
    id, tenant_id, patient_id, practitioner_id,
    scheduled_at, duration_minutes, appointment_type, status, reason,
    created_at, updated_at
  ) VALUES (
    a1, tid, p_sipho, dr_khumalo,
    (CURRENT_DATE + INTERVAL '1 day' + INTERVAL '9 hours'),
    30, 'FOLLOW_UP', 'SCHEDULED', 'Diabetes medication review',
    NOW(), NOW()
  ) ON CONFLICT DO NOTHING;

  -- Fatima — upcoming annual check (next week)
  INSERT INTO clinic_appointments (
    id, tenant_id, patient_id, practitioner_id,
    scheduled_at, duration_minutes, appointment_type, status, reason,
    created_at, updated_at
  ) VALUES (
    a2, tid, p_fatima, dr_berg,
    (CURRENT_DATE + INTERVAL '7 days' + INTERVAL '14 hours'),
    45, 'CONSULTATION', 'CONFIRMED', 'Annual cardiac check-up',
    NOW(), NOW()
  ) ON CONFLICT DO NOTHING;

  -- Jane — confirmed today (14:00)
  INSERT INTO clinic_appointments (
    id, tenant_id, patient_id, practitioner_id,
    scheduled_at, duration_minutes, appointment_type, status, reason,
    created_at, updated_at
  ) VALUES (
    a3, tid, p_jane, dr_khumalo,
    (CURRENT_DATE + INTERVAL '14 hours'),
    30, 'CONSULTATION', 'CONFIRMED', 'General wellness check',
    NOW(), NOW()
  ) ON CONFLICT DO NOTHING;

  -- Alex — completed paeds check last week
  INSERT INTO clinic_appointments (
    id, tenant_id, patient_id, practitioner_id,
    scheduled_at, duration_minutes, appointment_type, status, reason,
    created_at, updated_at
  ) VALUES (
    a4, tid, p_alex, dr_govender,
    NOW() - INTERVAL '5 days',
    20, 'CHECKUP', 'COMPLETED', 'Routine growth and development check',
    NOW() - INTERVAL '5 days', NOW()
  ) ON CONFLICT DO NOTHING;

  -- Dorothy — no-show last month
  INSERT INTO clinic_appointments (
    id, tenant_id, patient_id, practitioner_id,
    scheduled_at, duration_minutes, appointment_type, status, reason,
    created_at, updated_at
  ) VALUES (
    a5, tid, p_gran, dr_mokoena,
    NOW() - INTERVAL '30 days',
    30, 'FOLLOW_UP', 'NO_SHOW', 'Blood pressure review',
    NOW() - INTERVAL '30 days', NOW()
  ) ON CONFLICT DO NOTHING;

  -- Liam — completed last month (the one visible in screenshot)
  INSERT INTO clinic_appointments (
    id, tenant_id, patient_id, practitioner_id,
    scheduled_at, duration_minutes, appointment_type, status, reason,
    created_at, updated_at
  ) VALUES (
    a6, tid, p_liam, dr_govender,
    '2026-06-08 09:30:00',
    20, 'FOLLOW_UP', 'COMPLETED', '5-year growth and development check',
    '2026-06-08 09:00:00', '2026-06-08 10:00:00'
  ) ON CONFLICT DO NOTHING;

  -- ── CONSULTATIONS ──────────────────────────────────────────────────────────

  -- Sipho — diabetes review 14 days ago
  INSERT INTO clinic_consultations (
    id, tenant_id, patient_id, practitioner_id, appointment_id,
    consulted_at,
    weight_kg, height_cm, blood_pressure, pulse_bpm, temperature_c, oxygen_sat_pct,
    chief_complaint, history, examination, diagnosis, icd10_codes, treatment_plan,
    follow_up_days, billed, billing_amount, created_at, updated_at
  ) VALUES (
    c1, tid, p_sipho, dr_khumalo, NULL,
    NOW() - INTERVAL '14 days',
    82, 172, '148/92', 74, 36.5, 98,
    'Routine diabetes and hypertension review',
    'Known T2DM x 8 years on Metformin 1g BD and Glibenclamide 5mg. BP has been elevated at home readings around 150/95. HbA1c last checked 3 months ago at 8.1%. Reports poor dietary compliance over the holidays.',
    'BP 148/92 — elevated. Weight stable at 82kg. Feet examination normal. No peripheral oedema. Urine dipstick — trace protein.',
    'Type 2 diabetes mellitus — suboptimally controlled. Essential hypertension — uncontrolled.',
    ARRAY['E11.9', 'I10'],
    'Increase Amlodipine from 5mg to 10mg OD. Continue Metformin and Glibenclamide. Reinforce dietary education — reduce refined carbohydrates. Repeat HbA1c and renal function in 3 months. Patient counselled on DASH diet.',
    90, true, 520.00,
    NOW() - INTERVAL '14 days', NOW()
  ) ON CONFLICT DO NOTHING;

  -- Fatima — hypertension consult 7 days ago
  INSERT INTO clinic_consultations (
    id, tenant_id, patient_id, practitioner_id, appointment_id,
    consulted_at,
    weight_kg, height_cm, blood_pressure, pulse_bpm, temperature_c, oxygen_sat_pct,
    chief_complaint, history, examination, diagnosis, icd10_codes, treatment_plan,
    follow_up_days, billed, billing_amount, created_at, updated_at
  ) VALUES (
    c2, tid, p_fatima, dr_berg, NULL,
    NOW() - INTERVAL '7 days',
    68, 158, '162/98', 80, 36.7, 97,
    'Chest tightness and shortness of breath on exertion',
    'Known hypertensive and hypercholesterolaemic. On Amlodipine 10mg and Atorvastatin 40mg. Reports new onset chest tightness on climbing stairs over past 2 weeks. No chest pain at rest. No orthopnoea or PND.',
    'BP 162/98 — significantly elevated. Heart sounds normal, no added sounds. Lungs clear. No ankle oedema. 12-lead ECG — normal sinus rhythm, no ST changes.',
    'Hypertensive urgency. Possible angina — needs further investigation.',
    ARRAY['I16.0', 'I20.9'],
    'Add Bisoprolol 5mg OD to current regimen. Refer to cardiology for stress ECG and possible angiography. Increase Amlodipine to 10mg. Advise to avoid strenuous activity until cardiac clearance. RV in 1 week.',
    7, false, 0.00,
    NOW() - INTERVAL '7 days', NOW()
  ) ON CONFLICT DO NOTHING;

  -- Dorothy — hypertension follow-up 2 months ago
  INSERT INTO clinic_consultations (
    id, tenant_id, patient_id, practitioner_id, appointment_id,
    consulted_at,
    weight_kg, height_cm, blood_pressure, pulse_bpm, temperature_c, oxygen_sat_pct,
    chief_complaint, history, examination, diagnosis, icd10_codes, treatment_plan,
    follow_up_days, billed, billing_amount, created_at, updated_at
  ) VALUES (
    c3, tid, p_gran, dr_mokoena, NULL,
    NOW() - INTERVAL '60 days',
    74, 160, '138/86', 76, 36.6, 97,
    'Blood pressure follow-up and diabetes management',
    'T2DM and hypertension on Metformin 500mg BD and Losartan 50mg OD. BP improved since last visit. Blood sugar readings at home averaging 9-11 mmol/L. Mild knee pain.',
    'BP 138/86 — improved. Weight 74kg. Mild bilateral knee crepitus. Feet neurovascular intact.',
    'Type 2 diabetes mellitus — partially controlled. Hypertension — improving.',
    ARRAY['E11.9', 'I10'],
    'Continue current medications. Add Paracetamol 1g TDS PRN for knee pain. Advise low-impact exercise (swimming, walking). Repeat fasting glucose in 1 month.',
    30, true, 520.00,
    NOW() - INTERVAL '60 days', NOW()
  ) ON CONFLICT DO NOTHING;

  -- Jane — general wellness 1 month ago
  INSERT INTO clinic_consultations (
    id, tenant_id, patient_id, practitioner_id, appointment_id,
    consulted_at,
    weight_kg, height_cm, blood_pressure, pulse_bpm, temperature_c, oxygen_sat_pct,
    chief_complaint, history, examination, diagnosis, icd10_codes, treatment_plan,
    follow_up_days, billed, billing_amount, created_at, updated_at
  ) VALUES (
    c4, tid, p_jane, dr_khumalo, NULL,
    NOW() - INTERVAL '30 days',
    62, 165, '118/76', 68, 36.4, 99,
    'Routine wellness check — annual',
    'Well woman. No significant past medical history. Non-smoker. Occasional alcohol. Works as accountant. Reports fatigue and mild headaches over past month. No chest symptoms. Last Pap smear 2 years ago.',
    'BP 118/76 — normal. Weight 62kg, BMI 22.8. Breast examination normal. Abdomen soft, no organomegaly. Thyroid not palpable.',
    'General wellness check — no acute findings. Fatigue possibly related to iron deficiency — to investigate.',
    ARRAY['Z00.0'],
    'Request FBC and ferritin to exclude iron deficiency anaemia. Advise on sleep hygiene. Recommend Pap smear — overdue. Multi-vitamin supplementation. RV with results.',
    21, false, 0.00,
    NOW() - INTERVAL '30 days', NOW()
  ) ON CONFLICT DO NOTHING;

  -- Update last_visit_at on patients with consultations
  UPDATE clinic_patients SET last_visit_at = NOW() - INTERVAL '14 days' WHERE id = p_sipho;
  UPDATE clinic_patients SET last_visit_at = NOW() - INTERVAL '7 days'  WHERE id = p_fatima;
  UPDATE clinic_patients SET last_visit_at = NOW() - INTERVAL '60 days' WHERE id = p_gran;
  UPDATE clinic_patients SET last_visit_at = NOW() - INTERVAL '30 days' WHERE id = p_jane;

  -- ── PRESCRIPTIONS ──────────────────────────────────────────────────────────

  -- Sipho — rx from consultation c1
  INSERT INTO clinic_prescriptions (
    id, tenant_id, consultation_id, patient_id, practitioner_id,
    medication_name, nappi_code, dosage, frequency, duration,
    quantity, repeats, instructions, dispensed, prescribed_at, created_at, updated_at
  ) VALUES
  (rx1, tid, c1, p_sipho, dr_khumalo,
   'Amlodipine', '703200001', '10mg', 'Once daily (morning)', '90 days',
   90, 2, 'Take in the morning with or without food. Do not stop suddenly.',
   false, NOW() - INTERVAL '14 days', NOW() - INTERVAL '14 days', NOW()),
  (rx2, tid, c1, p_sipho, dr_khumalo,
   'Metformin', '704100001', '1g', 'Twice daily (with meals)', '90 days',
   180, 2, 'Take with breakfast and supper. Do not crush tablets.',
   true, NOW() - INTERVAL '14 days', NOW() - INTERVAL '14 days', NOW()),
  (rx3, tid, c1, p_sipho, dr_khumalo,
   'Glibenclamide', '704100002', '5mg', 'Once daily (30 min before breakfast)', '90 days',
   90, 2, 'Take 30 minutes before breakfast. Monitor for hypoglycaemia.',
   true, NOW() - INTERVAL '14 days', NOW() - INTERVAL '14 days', NOW())
  ON CONFLICT DO NOTHING;

  -- Fatima — rx from c2
  INSERT INTO clinic_prescriptions (
    id, tenant_id, consultation_id, patient_id, practitioner_id,
    medication_name, nappi_code, dosage, frequency, duration,
    quantity, repeats, instructions, dispensed, prescribed_at, created_at, updated_at
  ) VALUES
  (rx4, tid, c2, p_fatima, dr_berg,
   'Bisoprolol', '703200005', '5mg', 'Once daily (morning)', '30 days',
   30, 1, 'Take at the same time each day. Do not stop suddenly — wean off if stopping.',
   false, NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days', NOW()),
  (rx5, tid, c2, p_fatima, dr_berg,
   'Amlodipine', '703200001', '10mg', 'Once daily', '30 days',
   30, 1, 'Continue previous amlodipine — dose increased to 10mg.',
   false, NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days', NOW())
  ON CONFLICT DO NOTHING;

  -- ── CLAIMS ────────────────────────────────────────────────────────────────

  -- Claim for Sipho's consultation — PAID
  INSERT INTO clinic_claims (
    id, tenant_id, consultation_id, patient_id, practitioner_id,
    status, scheme_name, member_number, dependent_code,
    gross_amount, scheme_portion, patient_portion,
    submitted_at, reference_number,
    created_at, updated_at
  ) VALUES (
    cl1, tid, c1, p_sipho, dr_khumalo,
    'PAID', 'Discovery Health', 'DH12345678', '00',
    520.00, 468.00, 52.00,
    NOW() - INTERVAL '13 days', 'DH-2026-001234',
    NOW() - INTERVAL '14 days', NOW()
  ) ON CONFLICT DO NOTHING;

  INSERT INTO clinic_claim_lines (
    id, claim_id, line_type, tariff_code, icd10_code, description,
    quantity, unit_price, gross_amount, scheme_portion, patient_portion,
    sort_order, created_at
  ) VALUES
  (cll1, cl1, 'CONSULTATION', '0191', 'E11.9',
   'Consultation — established patient, intermediate',
   1, 520.00, 520.00, 468.00, 52.00, 0, NOW()),
  (cll2, cl1, 'MEDICINE', NULL, 'E11.9',
   'Metformin 1g (x90)',
   90, 0.42, 38.00, 34.20, 3.80, 1, NOW())
  ON CONFLICT DO NOTHING;

  -- Claim for Dorothy's consultation — SUBMITTED (awaiting)
  INSERT INTO clinic_claims (
    id, tenant_id, consultation_id, patient_id, practitioner_id,
    status, scheme_name, member_number, dependent_code,
    gross_amount, scheme_portion, patient_portion,
    submitted_at,
    created_at, updated_at
  ) VALUES (
    cl2, tid, c3, p_gran, dr_mokoena,
    'SUBMITTED', 'Bonitas', 'BON87654321', '02',
    520.00, 416.00, 104.00,
    NOW() - INTERVAL '55 days',
    NOW() - INTERVAL '60 days', NOW()
  ) ON CONFLICT DO NOTHING;

  INSERT INTO clinic_claim_lines (
    id, claim_id, line_type, tariff_code, icd10_code, description,
    quantity, unit_price, gross_amount, scheme_portion, patient_portion,
    sort_order, created_at
  ) VALUES
  (cll3, cl2, 'CONSULTATION', '0191', 'E11.9',
   'Consultation — established patient, intermediate',
   1, 520.00, 520.00, 416.00, 104.00, 0, NOW()),
  (cll4, cl2, 'MEDICINE', NULL, 'I10',
   'Losartan 50mg (x30)',
   30, 4.17, 125.00, 100.00, 25.00, 1, NOW())
  ON CONFLICT DO NOTHING;

  -- ── LAB RESULTS ───────────────────────────────────────────────────────────

  -- Sipho — HbA1c and renal function (REVIEWED, with AI interpretation)
  INSERT INTO clinic_lab_results (
    id, tenant_id, patient_id, consultation_id,
    source, lab_reference, collected_at, received_at,
    pdf_filename, status,
    patient_name_raw, parsed_markers,
    interpretation,
    notified, created_at, updated_at
  ) VALUES (
    lr1, tid, p_sipho, c1,
    'AMPATH', 'AMP-2026-004521', NOW() - INTERVAL '15 days', NOW() - INTERVAL '14 days',
    'Nkosi_Sipho_HbA1c_Renal_20260604.pdf', 'REVIEWED',
    'Nkosi S',
    '[
      {"marker":"HbA1c","value":"8.4","unit":"%","refRange":"<7.0","flag":"HIGH"},
      {"marker":"Fasting Glucose","value":"11.2","unit":"mmol/L","refRange":"4.0-6.0","flag":"HIGH"},
      {"marker":"Creatinine","value":"98","unit":"µmol/L","refRange":"64-104","flag":"NORMAL"},
      {"marker":"eGFR","value":"72","unit":"mL/min/1.73m²","refRange":">60","flag":"NORMAL"},
      {"marker":"Urea","value":"6.8","unit":"mmol/L","refRange":"2.5-7.8","flag":"NORMAL"},
      {"marker":"Sodium","value":"139","unit":"mmol/L","refRange":"136-145","flag":"NORMAL"},
      {"marker":"Potassium","value":"4.1","unit":"mmol/L","refRange":"3.5-5.1","flag":"NORMAL"},
      {"marker":"Total Cholesterol","value":"6.2","unit":"mmol/L","refRange":"<5.0","flag":"HIGH"},
      {"marker":"LDL","value":"4.1","unit":"mmol/L","refRange":"<3.0","flag":"HIGH"},
      {"marker":"HDL","value":"0.9","unit":"mmol/L","refRange":">1.0","flag":"LOW"},
      {"marker":"Triglycerides","value":"2.8","unit":"mmol/L","refRange":"<1.7","flag":"HIGH"}
    ]'::jsonb,
    'HbA1c of 8.4% indicates suboptimal glycaemic control — target is below 7.0% for this patient. Fasting glucose of 11.2 mmol/L is significantly elevated. Renal function is preserved with eGFR 72 and normal creatinine. Lipid profile is concerning with elevated total cholesterol (6.2), raised LDL (4.1) and low HDL (0.9) — suggest adding statin therapy or reviewing current dose. Overall: diabetes poorly controlled, dyslipidaemia requires attention.',
    true, NOW() - INTERVAL '14 days', NOW()
  ) ON CONFLICT DO NOTHING;

  -- Fatima — full blood count (UNREVIEWED — doctor needs to action)
  INSERT INTO clinic_lab_results (
    id, tenant_id, patient_id, consultation_id,
    source, lab_reference, collected_at, received_at,
    pdf_filename, status,
    patient_name_raw, parsed_markers,
    notified, created_at, updated_at
  ) VALUES (
    lr2, tid, p_fatima, c2,
    'LANCET', 'LAN-2026-009871', NOW() - INTERVAL '6 days', NOW() - INTERVAL '5 days',
    'Moosa_Fatima_FBC_Cardiac_20260612.pdf', 'UNREVIEWED',
    'Moosa Fatima',
    '[
      {"marker":"Haemoglobin","value":"11.2","unit":"g/dL","refRange":"12.0-16.0","flag":"LOW"},
      {"marker":"MCV","value":"72","unit":"fL","refRange":"80-100","flag":"LOW"},
      {"marker":"MCH","value":"23","unit":"pg","refRange":"27-34","flag":"LOW"},
      {"marker":"Ferritin","value":"6","unit":"µg/L","refRange":"12-150","flag":"LOW"},
      {"marker":"WBC","value":"8.1","unit":"×10⁹/L","refRange":"4.0-11.0","flag":"NORMAL"},
      {"marker":"Platelets","value":"298","unit":"×10⁹/L","refRange":"150-400","flag":"NORMAL"},
      {"marker":"CRP","value":"18","unit":"mg/L","refRange":"<10","flag":"HIGH"},
      {"marker":"Troponin I","value":"0.008","unit":"µg/L","refRange":"<0.012","flag":"NORMAL"},
      {"marker":"BNP","value":"145","unit":"pg/mL","refRange":"<100","flag":"HIGH"}
    ]'::jsonb,
    false, NOW() - INTERVAL '5 days', NOW()
  ) ON CONFLICT DO NOTHING;

  RAISE NOTICE 'QA test data inserted successfully';
  RAISE NOTICE 'Individual patients: Sipho Nkosi, Fatima Moosa, Liam Botha';
  RAISE NOTICE 'Family account (PRINCIPAL): Jane Dlamini';
  RAISE NOTICE '  Dependants: Thomas (spouse), Alex (child), Dorothy (grandparent)';
  RAISE NOTICE 'Appointments: 6 (1 scheduled, 1 confirmed today, 1 confirmed future, 2 completed, 1 no-show)';
  RAISE NOTICE 'Consultations: 4 with full SOAP notes, vitals, ICD-10';
  RAISE NOTICE 'Prescriptions: 5 (3 for Sipho, 2 for Fatima)';
  RAISE NOTICE 'Claims: 2 (Sipho=PAID, Dorothy=SUBMITTED)';
  RAISE NOTICE 'Lab results: 2 (Sipho=REVIEWED with AI, Fatima=UNREVIEWED)';

END $$;
