-- Guard complaints: a complaint about a guard, investigated, found, acted on and closed, with a timeline.
-- Evidence files go through the shared evidence module (entity type GuardComplaint).

CREATE TABLE security_guard_complaints (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    complaint_number     VARCHAR(40) NOT NULL,
    guard_id             UUID NOT NULL REFERENCES security_guards(id),
    site_id              UUID,
    occurred_on          DATE NOT NULL,
    category             VARCHAR(40) NOT NULL
        CHECK (category IN (
            'ABSENTEEISM', 'LATENESS', 'MISCONDUCT', 'SLEEPING_ON_DUTY', 'NEGLIGENCE', 'POOR_CUSTOMER_SERVICE',
            'FAILURE_TO_PATROL', 'FAILURE_TO_FOLLOW_POST_ORDERS', 'DISHONESTY', 'THEFT', 'EXCESSIVE_FORCE',
            'HARASSMENT', 'INTOXICATION', 'FIREARM_VIOLATION', 'ACCESS_CONTROL_VIOLATION', 'OTHER')),
    severity             VARCHAR(20) NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    description          TEXT NOT NULL,
    complainant_type     VARCHAR(30) NOT NULL
        CHECK (complainant_type IN ('CLIENT', 'SUPERVISOR', 'COLLEAGUE', 'MEMBER_OF_PUBLIC', 'INTERNAL', 'OTHER')),
    complainant_name     VARCHAR(200),
    complainant_contact  VARCHAR(200),
    witnesses            TEXT,
    status               VARCHAR(30) NOT NULL
        CHECK (status IN ('RECEIVED', 'UNDER_INVESTIGATION', 'FINDING_MADE', 'ACTION_TAKEN', 'CLOSED', 'WITHDRAWN')),
    investigator_name    VARCHAR(200),
    finding              VARCHAR(30) CHECK (finding IS NULL OR finding IN ('SUBSTANTIATED', 'UNSUBSTANTIATED', 'INCONCLUSIVE')),
    finding_note         TEXT,
    finding_by_name      VARCHAR(200),
    finding_at           TIMESTAMPTZ,
    action               VARCHAR(30)
        CHECK (action IS NULL OR action IN (
            'NO_ACTION', 'COUNSELLING', 'VERBAL_WARNING', 'WRITTEN_WARNING', 'FINAL_WARNING',
            'RETRAINING', 'SUSPENSION', 'DISCIPLINARY_HEARING')),
    action_note          TEXT,
    action_by_name       VARCHAR(200),
    action_at            TIMESTAMPTZ,
    resolution_note      TEXT,
    closed_by_name       VARCHAR(200),
    closed_at            TIMESTAMPTZ,
    withdrawn_reason     TEXT,
    created_by           UUID,
    created_by_name      VARCHAR(200),
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    UNIQUE (tenant_id, complaint_number)
);

CREATE INDEX idx_guard_complaints_guard ON security_guard_complaints (tenant_id, guard_id, occurred_on DESC);
CREATE INDEX idx_guard_complaints_status ON security_guard_complaints (tenant_id, status);

CREATE TABLE security_guard_complaint_events (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    complaint_id  UUID NOT NULL REFERENCES security_guard_complaints(id),
    event_type    VARCHAR(40) NOT NULL,
    to_status     VARCHAR(30),
    note          TEXT,
    by_name       VARCHAR(200),
    by_user       UUID,
    at            TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_guard_complaint_events ON security_guard_complaint_events (complaint_id, at);

COMMENT ON TABLE security_guard_complaints IS
    'Complaints about guards with an investigation workflow. Never deleted: closed or withdrawn complaints stay as the record.';
