-- Who changed a patient's name, ID number, date of birth or sex, when, and what it was before (patch 0176).
-- Append-only: a correction can be explained but never rewritten or removed.

CREATE TABLE IF NOT EXISTS clinic_patient_corrections (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL REFERENCES tenants(id),
    patient_id  UUID         NOT NULL REFERENCES clinic_patients(id),
    field       VARCHAR(20)  NOT NULL CHECK (field IN ('FIRST_NAME','LAST_NAME','ID_NUMBER','DATE_OF_BIRTH','GENDER','SEX_AT_BIRTH')),
    old_value   VARCHAR(100),
    new_value   VARCHAR(100),
    changed_by  UUID,
    changed_at  TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_clinic_patient_corrections_patient ON clinic_patient_corrections (tenant_id, patient_id, changed_at DESC);

CREATE OR REPLACE FUNCTION clinic_corrections_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'clinic_patient_corrections is append-only';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_clinic_patient_corrections_append_only ON clinic_patient_corrections;
CREATE TRIGGER trg_clinic_patient_corrections_append_only
    BEFORE UPDATE OR DELETE ON clinic_patient_corrections FOR EACH ROW EXECUTE FUNCTION clinic_corrections_append_only();
