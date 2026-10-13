-- Red flag review (Q-5, patch 0181). A red flag raised by a questionnaire stays on the consultation until a clinician has reviewed it.
-- REVIEWED = the clinician has seen it (not that it is resolved). OVERRIDDEN = the visit was signed without reviewing it, with a reason.
-- Append-only: the audit trail of who saw or overrode which flag, and when, cannot be edited or removed.

CREATE TABLE IF NOT EXISTS clinic_red_flag_review (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    consultation_id UUID         NOT NULL REFERENCES clinic_consultations(id),
    group_code      VARCHAR(60)  NOT NULL,
    flag_code       VARCHAR(60)  NOT NULL,
    flag_label      VARCHAR(200) NOT NULL,
    severity        VARCHAR(10)  NOT NULL,
    outcome         VARCHAR(12)  NOT NULL CHECK (outcome IN ('REVIEWED','OVERRIDDEN')),
    reason          VARCHAR(500),
    acted_by        UUID,
    acted_at        TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT chk_red_flag_override_reason CHECK (outcome <> 'OVERRIDDEN' OR (reason IS NOT NULL AND length(btrim(reason)) > 0))
);
CREATE INDEX IF NOT EXISTS idx_clinic_red_flag_review_consult ON clinic_red_flag_review (tenant_id, consultation_id, acted_at DESC);

CREATE OR REPLACE FUNCTION clinic_red_flag_review_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'clinic_red_flag_review is append-only';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_clinic_red_flag_review_append_only ON clinic_red_flag_review;
CREATE TRIGGER trg_clinic_red_flag_review_append_only
    BEFORE UPDATE OR DELETE ON clinic_red_flag_review FOR EACH ROW EXECUTE FUNCTION clinic_red_flag_review_append_only();
