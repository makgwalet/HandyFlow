-- Compliance Services: cases. A case is a problem being worked on for a client, usually opened from a failed assessment
-- item. It has an owner, a due date and steps, and cannot be resolved while steps are open. Closing needs a second
-- person to confirm. Every change is kept in an event trail, and nothing is deleted.

CREATE TABLE IF NOT EXISTS compliance_cases (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    client_id               UUID NOT NULL REFERENCES compliance_service_clients(id),
    title                   VARCHAR(200) NOT NULL,
    description             TEXT,
    severity                VARCHAR(10) NOT NULL,
    status                  VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    owner_id                UUID,
    due_date                DATE,
    assessment_id           UUID,
    obligation_id           UUID,
    obligation_code         VARCHAR(60),
    resolution_note         TEXT,
    resolved_by             UUID,
    resolved_at             TIMESTAMPTZ,
    closed_by               UUID,
    closed_at               TIMESTAMPTZ,
    close_exception_reason  TEXT,
    cancel_reason           TEXT,
    created_by              UUID,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_case_status CHECK (status IN ('OPEN','IN_PROGRESS','AWAITING_CLIENT','RESOLVED','CLOSED','CANCELLED')),
    CONSTRAINT ck_case_severity CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_case_states CHECK (
        (status NOT IN ('RESOLVED','CLOSED') OR (resolution_note IS NOT NULL AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL))
        AND (status <> 'CLOSED' OR (closed_by IS NOT NULL AND closed_at IS NOT NULL))
        AND (status <> 'CANCELLED' OR cancel_reason IS NOT NULL)
        AND (status = 'OPEN' OR owner_id IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS idx_compliance_cases_client ON compliance_cases(tenant_id, client_id);
-- one live case per client and obligation
CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_case_live ON compliance_cases(client_id, obligation_id)
    WHERE obligation_id IS NOT NULL AND status NOT IN ('CLOSED','CANCELLED');

CREATE TABLE IF NOT EXISTS compliance_case_steps (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    case_id        UUID NOT NULL REFERENCES compliance_cases(id) ON DELETE CASCADE,
    description    VARCHAR(500) NOT NULL,
    status         VARCHAR(10) NOT NULL DEFAULT 'TODO',
    due_date       DATE,
    sort_order     INTEGER NOT NULL DEFAULT 0,
    done_by        UUID,
    done_at        TIMESTAMPTZ,
    cancel_reason  TEXT,
    CONSTRAINT ck_case_step_status CHECK (status IN ('TODO','DONE','CANCELLED')),
    CONSTRAINT ck_case_step_states CHECK ((status <> 'DONE' OR done_at IS NOT NULL) AND (status <> 'CANCELLED' OR cancel_reason IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS idx_compliance_case_steps ON compliance_case_steps(tenant_id, case_id);

CREATE TABLE IF NOT EXISTS compliance_case_events (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    case_id      UUID NOT NULL,
    event        VARCHAR(24) NOT NULL,
    from_status  VARCHAR(16),
    to_status    VARCHAR(16),
    note         TEXT,
    actor        UUID,
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_compliance_case_events ON compliance_case_events(tenant_id, case_id, occurred_at);
