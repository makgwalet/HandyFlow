-- A clinician may sign a consultation that lacks a required step (Symptoms, Diagnosis) only by giving a reason.
-- One row per overridden step, kept for audit (REQUIRED_STEP_OVERRIDDEN). Rows are never updated or deleted.
CREATE TABLE IF NOT EXISTS clinic_consultation_overrides (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    consultation_id UUID         NOT NULL REFERENCES clinic_consultations(id),
    step            VARCHAR(30)  NOT NULL,
    reason          VARCHAR(500) NOT NULL,
    overridden_by   UUID,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT chk_override_step CHECK (step IN ('SYMPTOMS','DIAGNOSIS'))
);
CREATE INDEX IF NOT EXISTS idx_clinic_consult_overrides ON clinic_consultation_overrides (tenant_id, consultation_id);
