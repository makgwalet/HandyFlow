-- Rename this file to the actual next Flyway version before running.

CREATE TABLE clinic_patient_consents (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    patient_id         UUID NOT NULL,
    consent_type       VARCHAR(50) NOT NULL,
    action             VARCHAR(20) NOT NULL,
    method             VARCHAR(20),
    captured_by_name   VARCHAR(255),
    notes              TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_clinic_patient_consents_patient
    ON clinic_patient_consents (tenant_id, patient_id, consent_type, created_at DESC);