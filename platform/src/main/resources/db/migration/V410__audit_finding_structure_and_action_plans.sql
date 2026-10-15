-- V410: A8. Findings gain criteria, condition and consequence (existing findings keep their description and leave these
-- empty). Management action plans become their own records: owner, due date, completion with a note, and validation by
-- someone other than the person who completed it and the plan owner. A finding that has action plans cannot be closed
-- until each is validated or cancelled.
ALTER TABLE audit_findings
    ADD COLUMN criteria    TEXT,
    ADD COLUMN condition   TEXT,
    ADD COLUMN consequence TEXT;

CREATE TABLE audit_action_plans (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID         NOT NULL,
    finding_id        UUID         NOT NULL REFERENCES audit_findings (id),
    description       TEXT         NOT NULL,
    owner             UUID,
    due_date          DATE         NOT NULL,
    status            VARCHAR(12)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'COMPLETED', 'VALIDATED', 'CANCELLED')),
    completion_note   TEXT,
    completed_by      UUID,
    completed_at      TIMESTAMPTZ,
    validation_note   TEXT,
    validated_by      UUID,
    validated_at      TIMESTAMPTZ,
    times_returned    INT          NOT NULL DEFAULT 0,
    cancel_reason     TEXT,
    created_by        UUID,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_action_plans_finding ON audit_action_plans (tenant_id, finding_id);
CREATE INDEX idx_audit_action_plans_due ON audit_action_plans (tenant_id, due_date) WHERE status IN ('OPEN', 'COMPLETED');
