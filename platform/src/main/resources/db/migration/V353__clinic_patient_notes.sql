-- Sticky notes and alerts on a patient's file. An ALERT is a note the whole team must see whenever the file is opened
-- (for example "needs an interpreter" or "do not leave voicemail"). Notes are never edited or deleted: a note that no
-- longer applies is RESOLVED, so the record keeps who said what and when.
CREATE TABLE IF NOT EXISTS clinic_patient_notes (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL,
    patient_id  UUID        NOT NULL,
    kind        VARCHAR(10) NOT NULL CHECK (kind IN ('NOTE', 'ALERT')),
    severity    VARCHAR(10) CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    body        TEXT        NOT NULL,
    created_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    resolved_by UUID
);
CREATE INDEX IF NOT EXISTS idx_clinic_patient_notes_patient
    ON clinic_patient_notes (tenant_id, patient_id, created_at DESC);
