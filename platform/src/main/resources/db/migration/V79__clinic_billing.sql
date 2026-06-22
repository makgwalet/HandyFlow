-- WHY a separate billing model?
-- In SA medical billing, a clinical note ≠ a claim.
-- A claim to a medical aid must have:
--   1. A consultation tariff line with its own ICD-10 code
--   2. Individual procedure lines (NRPL tariff codes)
--   3. Prescription lines with NAPPI codes
-- All at the line level, not just the consultation level.
-- This model captures the full claim structure for Healthbridge/switch submission.

-- Procedure tariff catalogue (NRPL codes)
CREATE TABLE clinic_procedure_catalogue (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID REFERENCES tenants(id),          -- NULL = system/global
    tariff_code     VARCHAR(20) NOT NULL,
    description     VARCHAR(200) NOT NULL,
    specialty       VARCHAR(100),                          -- which specialty uses this
    base_rate_zar   NUMERIC(10,2),                         -- base rate in ZAR
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_clinic_proc_catalogue_code ON clinic_procedure_catalogue(tariff_code);
CREATE INDEX idx_clinic_proc_catalogue_spec ON clinic_procedure_catalogue(specialty);

-- Claims (one per consultation)
CREATE TABLE clinic_claims (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    consultation_id     UUID NOT NULL REFERENCES clinic_consultations(id),
    patient_id          UUID NOT NULL REFERENCES clinic_patients(id),
    practitioner_id     UUID REFERENCES clinic_practitioners(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','SUBMITTED','ACCEPTED','REJECTED','PAID','PARTIAL')),
    -- Medical aid
    scheme_name         VARCHAR(100),
    member_number       VARCHAR(50),
    dependent_code      VARCHAR(10),
    -- Totals (computed from lines)
    gross_amount        NUMERIC(12,2) NOT NULL DEFAULT 0,
    scheme_portion      NUMERIC(12,2) NOT NULL DEFAULT 0,
    patient_portion     NUMERIC(12,2) NOT NULL DEFAULT 0,
    -- Submission tracking
    submitted_at        TIMESTAMP,
    reference_number    VARCHAR(100),              -- switch/Healthbridge reference
    rejection_reason    TEXT,
    notes               TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_clinic_claims_tenant  ON clinic_claims(tenant_id);
CREATE INDEX idx_clinic_claims_patient ON clinic_claims(patient_id);
CREATE UNIQUE INDEX idx_clinic_claims_consultation ON clinic_claims(consultation_id);

-- Claim lines (consultation tariff + procedures + medicines)
CREATE TABLE clinic_claim_lines (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id        UUID NOT NULL REFERENCES clinic_claims(id) ON DELETE CASCADE,
    line_type       VARCHAR(20) NOT NULL
        CHECK (line_type IN ('CONSULTATION','PROCEDURE','MEDICINE','CONSUMABLE')),
    -- Coding
    tariff_code     VARCHAR(20),    -- NRPL tariff code for CONSULTATION/PROCEDURE
    nappi_code      VARCHAR(20),    -- NAPPI code for MEDICINE
    icd10_code      VARCHAR(20),    -- ICD-10 at line level (required per medical aid)
    description     VARCHAR(200) NOT NULL,
    -- Quantities / pricing
    quantity        NUMERIC(8,2) NOT NULL DEFAULT 1,
    unit_price      NUMERIC(12,2) NOT NULL,
    gross_amount    NUMERIC(12,2) NOT NULL,
    scheme_portion  NUMERIC(12,2) NOT NULL DEFAULT 0,
    patient_portion NUMERIC(12,2) NOT NULL DEFAULT 0,
    -- Reference to source
    prescription_id UUID REFERENCES clinic_prescriptions(id),
    sort_order      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_clinic_claim_lines_claim ON clinic_claim_lines(claim_id);

-- Seed common NRPL consultation tariff codes
INSERT INTO clinic_procedure_catalogue (tariff_code, description, specialty, base_rate_zar) VALUES
('0190', 'Consultation — established patient, brief',          'General Practitioner', 380.00),
('0191', 'Consultation — established patient, intermediate',   'General Practitioner', 520.00),
('0192', 'Consultation — established patient, comprehensive',  'General Practitioner', 750.00),
('0193', 'Consultation — new patient, comprehensive',          'General Practitioner', 850.00),
('0104', 'Emergency consultation',                             'General Practitioner', 950.00),
('0194', 'Telephonic consultation',                            'General Practitioner', 220.00),
('0195', 'Home visit',                                         'General Practitioner', 680.00),
-- Procedures
('0007', 'Wound suture — simple (per cm)',                     'General Practitioner', 180.00),
('0115', 'Injection — intramuscular',                          'General Practitioner', 85.00),
('0116', 'Injection — intravenous',                            'General Practitioner', 120.00),
('0201', 'Pap smear',                                          'Gynaecologist',        420.00),
('0202', 'Insertion of IUD',                                   'Gynaecologist',        680.00),
('4116', 'ECG — 12 lead',                                      'Cardiologist',         350.00),
('3610', 'Spirometry',                                         'General Practitioner', 280.00),
('2124', 'X-ray — chest, PA',                                  'Radiologist',          480.00);
