-- Patient profile details the registration form never held (patch 0166): how the patient is identified, where they live,
-- how to reach them, a second emergency contact, and whether they pay by medical aid or themselves.
-- One row per patient, created the first time the profile is saved.

CREATE TABLE IF NOT EXISTS clinic_patient_profile (
    patient_id           UUID PRIMARY KEY REFERENCES clinic_patients(id),
    tenant_id            UUID         NOT NULL REFERENCES tenants(id),
    title                VARCHAR(20),
    id_type              VARCHAR(10)  CHECK (id_type IS NULL OR id_type IN ('SA_ID','PASSPORT','OTHER')),
    nationality          VARCHAR(80),
    preferred_language   VARCHAR(40),
    preferred_contact    VARCHAR(10)  CHECK (preferred_contact IS NULL OR preferred_contact IN ('PHONE','SMS','WHATSAPP','EMAIL')),
    address_line1        VARCHAR(150),
    address_line2        VARCHAR(150),
    suburb               VARCHAR(80),
    city                 VARCHAR(80),
    province             VARCHAR(40),
    postal_code          VARCHAR(10),
    emergency_relationship VARCHAR(40),
    secondary_contact_name         VARCHAR(120),
    secondary_contact_phone        VARCHAR(30),
    secondary_contact_relationship VARCHAR(40),
    payment_type         VARCHAR(12)  CHECK (payment_type IS NULL OR payment_type IN ('MEDICAL_AID','SELF_PAY')),
    updated_by           UUID,
    updated_at           TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_clinic_patient_profile_tenant ON clinic_patient_profile (tenant_id);
