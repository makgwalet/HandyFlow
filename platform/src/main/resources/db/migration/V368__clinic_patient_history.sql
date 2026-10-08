-- Patient family history and lifestyle / social history (patch 0158).
-- Family history is a list of entries (relative, condition); lifestyle and social history is one record per patient.
-- Nothing is deleted: a wrong family entry is marked ENTERED_IN_ERROR. Medical aid already has its table (V19).

CREATE TABLE IF NOT EXISTS clinic_patient_family_history (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    patient_id      UUID         NOT NULL REFERENCES clinic_patients(id),
    relative        VARCHAR(20)  NOT NULL CHECK (relative IN ('MOTHER','FATHER','SIBLING','GRANDPARENT','CHILD','AUNT_UNCLE','COUSIN','OTHER')),
    condition_name  VARCHAR(200) NOT NULL,
    age_at_onset    SMALLINT CHECK (age_at_onset IS NULL OR age_at_onset BETWEEN 0 AND 120),
    deceased        BOOLEAN,
    notes           VARCHAR(1000),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ENTERED_IN_ERROR')),
    recorded_by     UUID,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_clinic_family_history_patient ON clinic_patient_family_history (tenant_id, patient_id);

CREATE TABLE IF NOT EXISTS clinic_patient_social_history (
    patient_id      UUID PRIMARY KEY REFERENCES clinic_patients(id),
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    smoking_status  VARCHAR(10)  NOT NULL DEFAULT 'UNKNOWN' CHECK (smoking_status IN ('NEVER','FORMER','CURRENT','UNKNOWN')),
    alcohol_use     VARCHAR(12)  NOT NULL DEFAULT 'UNKNOWN' CHECK (alcohol_use IN ('NONE','OCCASIONAL','REGULAR','HEAVY','UNKNOWN')),
    substance_use   VARCHAR(10)  NOT NULL DEFAULT 'UNKNOWN' CHECK (substance_use IN ('NONE','PAST','CURRENT','UNKNOWN')),
    occupation      VARCHAR(150),
    living_situation VARCHAR(200),
    physical_activity VARCHAR(200),
    notes           VARCHAR(1000),
    updated_by      UUID,
    updated_at      TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_clinic_social_history_tenant ON clinic_patient_social_history (tenant_id);
