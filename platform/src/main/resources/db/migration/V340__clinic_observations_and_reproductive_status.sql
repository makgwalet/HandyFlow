-- Clinic Sprint 1: observations as a first-class model, plus sex_at_birth and pregnancy status.

-- sex_at_birth, not gender, drives sex-specific question visibility. Left NULL (unknown)
-- for existing patients on purpose: gender is not the same thing and must not be copied.
ALTER TABLE clinic_patients
    ADD COLUMN sex_at_birth            VARCHAR(10),
    ADD COLUMN pregnancy_status        VARCHAR(15),
    ADD COLUMN expected_delivery_date  DATE,
    ADD CONSTRAINT chk_patient_sex_at_birth CHECK (sex_at_birth IS NULL OR sex_at_birth IN ('MALE','FEMALE','INTERSEX','UNKNOWN')),
    ADD CONSTRAINT chk_patient_pregnancy    CHECK (pregnancy_status IS NULL OR pregnancy_status IN ('NOT_PREGNANT','PREGNANT','UNKNOWN'));

CREATE TABLE clinic_observations (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id),
    patient_id       UUID NOT NULL REFERENCES clinic_patients(id),
    consultation_id  UUID REFERENCES clinic_consultations(id),
    code             VARCHAR(40)  NOT NULL,
    value_numeric    NUMERIC(12,4) NOT NULL,
    unit             VARCHAR(20)  NOT NULL,
    ref_low          NUMERIC(12,4),
    ref_high         NUMERIC(12,4),
    abnormal_flag    VARCHAR(10),
    taken_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    taken_by         UUID REFERENCES users(id),
    source           VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    status           VARCHAR(20)  NOT NULL DEFAULT 'FINAL',
    notes            TEXT,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_obs_flag   CHECK (abnormal_flag IS NULL OR abnormal_flag IN ('LOW','NORMAL','HIGH')),
    CONSTRAINT chk_obs_source CHECK (source IN ('MANUAL','CONSULTATION','DEVICE','IMPORTED')),
    CONSTRAINT chk_obs_status CHECK (status IN ('FINAL','ENTERED_IN_ERROR'))
);
CREATE INDEX idx_clinic_obs_patient_code ON clinic_observations (tenant_id, patient_id, code, taken_at DESC);
CREATE INDEX idx_clinic_obs_consultation ON clinic_observations (consultation_id) WHERE consultation_id IS NOT NULL;
