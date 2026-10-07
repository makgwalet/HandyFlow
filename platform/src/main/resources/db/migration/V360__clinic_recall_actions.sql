-- Work done on a follow-up recall: calls made, snoozes, dismissals. A recall itself is derived from the
-- latest finished consultation, so each action is keyed to that consultation: a newer visit starts clean.
CREATE TABLE IF NOT EXISTS clinic_recall_actions (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    consultation_id UUID         NOT NULL REFERENCES clinic_consultations(id),
    patient_id      UUID         NOT NULL,
    action_type     VARCHAR(20)  NOT NULL,
    outcome         VARCHAR(30),
    note            VARCHAR(500),
    snooze_until    DATE,
    created_by      UUID,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT chk_recall_action_type CHECK (action_type IN ('CONTACT','SNOOZE','DISMISS','REOPEN')),
    CONSTRAINT chk_recall_snooze CHECK (action_type <> 'SNOOZE' OR snooze_until IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_clinic_recall_actions ON clinic_recall_actions (tenant_id, consultation_id, created_at DESC);
