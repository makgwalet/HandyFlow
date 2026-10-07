-- Clinic Sprint 1: structured allergies, conditions and medications.
-- Replaces the free-text arrays on clinic_patients as the source of truth.
-- The arrays are kept as a denormalised mirror (names of active rows) so existing
-- screens and PDFs keep working while they move to the structured endpoints.

CREATE TABLE clinic_patient_allergies (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES tenants(id),
    patient_id     UUID NOT NULL REFERENCES clinic_patients(id),
    allergen       VARCHAR(200) NOT NULL,
    allergen_type  VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN',
    reaction       VARCHAR(300),
    severity       VARCHAR(20),
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    notes          TEXT,
    recorded_by    UUID REFERENCES users(id),
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_allergy_type     CHECK (allergen_type IN ('DRUG','FOOD','ENVIRONMENT','OTHER','UNKNOWN')),
    CONSTRAINT chk_allergy_severity CHECK (severity IS NULL OR severity IN ('MILD','MODERATE','SEVERE','LIFE_THREATENING')),
    CONSTRAINT chk_allergy_status   CHECK (status IN ('ACTIVE','RESOLVED','ENTERED_IN_ERROR'))
);
CREATE INDEX idx_clinic_allergies_patient ON clinic_patient_allergies (tenant_id, patient_id);

CREATE TABLE clinic_patient_conditions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES tenants(id),
    patient_id     UUID NOT NULL REFERENCES clinic_patients(id),
    condition_name VARCHAR(200) NOT NULL,
    icd10_code     VARCHAR(20),
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    onset_date     DATE,
    notes          TEXT,
    recorded_by    UUID REFERENCES users(id),
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_condition_status CHECK (status IN ('ACTIVE','CONTROLLED','RESOLVED','ENTERED_IN_ERROR'))
);
CREATE INDEX idx_clinic_conditions_patient ON clinic_patient_conditions (tenant_id, patient_id);

CREATE TABLE clinic_patient_medications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    patient_id      UUID NOT NULL REFERENCES clinic_patients(id),
    medicine_name   VARCHAR(200) NOT NULL,
    nappi_code      VARCHAR(20),
    dose            VARCHAR(100),
    frequency       VARCHAR(100),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    source          VARCHAR(20)  NOT NULL DEFAULT 'PATIENT_REPORTED',
    started_on      DATE,
    stopped_on      DATE,
    stop_reason     VARCHAR(300),
    prescription_id UUID REFERENCES clinic_prescriptions(id),
    notes           TEXT,
    recorded_by     UUID REFERENCES users(id),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_pmed_status CHECK (status IN ('ACTIVE','STOPPED','COMPLETED','ENTERED_IN_ERROR')),
    CONSTRAINT chk_pmed_source CHECK (source IN ('PATIENT_REPORTED','PRESCRIBED_HERE','EXTERNAL'))
);
CREATE INDEX idx_clinic_pmeds_patient ON clinic_patient_medications (tenant_id, patient_id);

-- Backfill from the legacy arrays (type unknown, severity unrecorded).
INSERT INTO clinic_patient_allergies (tenant_id, patient_id, allergen, allergen_type, status)
SELECT p.tenant_id, p.id, btrim(a.v), 'UNKNOWN', 'ACTIVE'
FROM clinic_patients p
CROSS JOIN LATERAL unnest(p.allergies) AS a(v)
WHERE p.deleted_at IS NULL AND btrim(a.v) <> '';

INSERT INTO clinic_patient_conditions (tenant_id, patient_id, condition_name, status)
SELECT p.tenant_id, p.id, btrim(c.v), 'ACTIVE'
FROM clinic_patients p
CROSS JOIN LATERAL unnest(p.chronic_conditions) AS c(v)
WHERE p.deleted_at IS NULL AND btrim(c.v) <> '';
