-- A15. Control exceptions get a risk rating assessed by a person, an owner and target date, a management response, evidence rules for
-- serious ones, and a follow-up check after they are closed. Existing exceptions are left unrated; they must be rated before they can be
-- resolved.
ALTER TABLE control_exceptions
    ADD COLUMN risk_rating        VARCHAR(10) CHECK (risk_rating IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    ADD COLUMN risk_rationale     TEXT,
    ADD COLUMN rated_by           UUID,
    ADD COLUMN rated_by_name      VARCHAR(255),
    ADD COLUMN rated_at           TIMESTAMPTZ,
    ADD COLUMN owner_id           UUID,
    ADD COLUMN owner_name         VARCHAR(255),
    ADD COLUMN target_date        DATE,
    ADD COLUMN response_text      TEXT,
    ADD COLUMN response_by        UUID,
    ADD COLUMN response_by_name   VARCHAR(255),
    ADD COLUMN response_at        TIMESTAMPTZ,
    ADD COLUMN follow_up_date     DATE,
    ADD COLUMN follow_up_at       TIMESTAMPTZ,
    ADD COLUMN follow_up_by       UUID,
    ADD COLUMN follow_up_by_name  VARCHAR(255),
    ADD COLUMN follow_up_outcome  VARCHAR(10) CHECK (follow_up_outcome IN ('HOLDING', 'RECURRED')),
    ADD COLUMN follow_up_note     TEXT,
    ADD COLUMN times_reopened     INT NOT NULL DEFAULT 0;

CREATE INDEX idx_control_exceptions_followup ON control_exceptions (tenant_id, follow_up_date) WHERE follow_up_date IS NOT NULL AND follow_up_at IS NULL;
