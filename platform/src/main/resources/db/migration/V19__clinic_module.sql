-- WHY? Clinic module supports GP practices, physiotherapy, dental, optometry.
-- Follows same multi-tenant pattern as all other modules.
-- HPCSA compliance: patient records must be retained for minimum 5 years.

-- Patients register
CREATE TABLE clinic_patients (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    full_name       VARCHAR(200) GENERATED ALWAYS AS (first_name || ' ' || last_name) STORED,
    id_number       VARCHAR(20),
    date_of_birth   DATE,
    gender          VARCHAR(10) CHECK (gender IN ('MALE','FEMALE','OTHER','UNKNOWN')),
    phone           VARCHAR(30),
    email           VARCHAR(200),
    address         JSONB,
    blood_type      VARCHAR(5),
    allergies       TEXT[],
    chronic_conditions TEXT[],
    emergency_contact_name  VARCHAR(100),
    emergency_contact_phone VARCHAR(30),
    notes           TEXT,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP,
    deleted_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

-- Medical aid details per patient
CREATE TABLE clinic_medical_aids (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    patient_id      UUID NOT NULL REFERENCES clinic_patients(id),
    scheme_name     VARCHAR(100) NOT NULL,
    plan_name       VARCHAR(100),
    member_number   VARCHAR(50) NOT NULL,
    dependent_code  VARCHAR(10),
    principal_member VARCHAR(200),
    scheme_contact_phone VARCHAR(30),
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Practitioners (doctors, physios, etc.)
CREATE TABLE clinic_practitioners (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    full_name       VARCHAR(200) GENERATED ALWAYS AS (first_name || ' ' || last_name) STORED,
    specialty       VARCHAR(100),
    hpcsa_number    VARCHAR(50),
    practice_number VARCHAR(50),
    phone           VARCHAR(30),
    email           VARCHAR(200),
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP,
    deleted_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

-- Appointments
CREATE TABLE clinic_appointments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    patient_id      UUID NOT NULL REFERENCES clinic_patients(id),
    practitioner_id UUID REFERENCES clinic_practitioners(id),
    scheduled_at    TIMESTAMP NOT NULL,
    duration_minutes INT NOT NULL DEFAULT 30,
    appointment_type VARCHAR(50) NOT NULL DEFAULT 'CONSULTATION'
        CHECK (appointment_type IN ('CONSULTATION','FOLLOW_UP','PROCEDURE','EMERGENCY','CHECKUP')),
    status          VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
        CHECK (status IN ('SCHEDULED','CONFIRMED','IN_PROGRESS','COMPLETED','CANCELLED','NO_SHOW')),
    reason          TEXT,
    notes           TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP,
    deleted_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

-- Consultations (clinical notes)
CREATE TABLE clinic_consultations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    appointment_id  UUID REFERENCES clinic_appointments(id),
    patient_id      UUID NOT NULL REFERENCES clinic_patients(id),
    practitioner_id UUID REFERENCES clinic_practitioners(id),
    consulted_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    -- Vitals
    weight_kg       NUMERIC(6,2),
    height_cm       NUMERIC(6,2),
    blood_pressure  VARCHAR(20),
    pulse_bpm       INT,
    temperature_c   NUMERIC(4,1),
    oxygen_sat_pct  NUMERIC(5,2),
    -- Clinical
    chief_complaint TEXT,
    history         TEXT,
    examination     TEXT,
    diagnosis       TEXT,
    icd10_codes     TEXT[],
    treatment_plan  TEXT,
    follow_up_days  INT,
    -- Billing
    billed          BOOLEAN NOT NULL DEFAULT false,
    billing_code    VARCHAR(20),
    billing_amount  NUMERIC(12,2),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP,
    deleted_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0
);

-- Prescriptions
CREATE TABLE clinic_prescriptions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    consultation_id UUID NOT NULL REFERENCES clinic_consultations(id),
    patient_id      UUID NOT NULL REFERENCES clinic_patients(id),
    practitioner_id UUID REFERENCES clinic_practitioners(id),
    prescribed_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    medication_name VARCHAR(200) NOT NULL,
    dosage          VARCHAR(100),
    frequency       VARCHAR(100),
    duration        VARCHAR(100),
    quantity        INT,
    repeats         INT NOT NULL DEFAULT 0,
    instructions    TEXT,
    dispensed       BOOLEAN NOT NULL DEFAULT false,
    dispensed_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_clinic_patients_tenant    ON clinic_patients(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_clinic_patients_id_number ON clinic_patients(tenant_id, id_number) WHERE deleted_at IS NULL;
CREATE INDEX idx_clinic_appointments_tenant   ON clinic_appointments(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_clinic_appointments_patient  ON clinic_appointments(patient_id);
CREATE INDEX idx_clinic_appointments_date     ON clinic_appointments(tenant_id, scheduled_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_clinic_consultations_patient ON clinic_consultations(patient_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_clinic_prescriptions_patient ON clinic_prescriptions(patient_id);