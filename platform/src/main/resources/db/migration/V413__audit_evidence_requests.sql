-- A12. Evidence requests between the business and its external auditors. Either side can raise one. The business answers with files
-- or says the item is not available; the auditor approves or sends it back. Each answer is kept as a round, so the history shows what
-- was sent, when, and why it was sent back.
CREATE TABLE audit_evidence_requests (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    engagement_id       UUID REFERENCES external_audit_engagements (id),
    reference           VARCHAR(20)  NOT NULL,
    title               VARCHAR(200) NOT NULL,
    description         TEXT,
    priority            VARCHAR(8)   NOT NULL DEFAULT 'MEDIUM' CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    due_date            DATE,
    assigned_to         UUID,
    requested_by_kind   VARCHAR(8)   NOT NULL CHECK (requested_by_kind IN ('STAFF', 'AUDITOR')),
    requested_by        UUID         NOT NULL,
    requested_by_label  VARCHAR(255),
    status              VARCHAR(30)  NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'SUBMITTED', 'NOT_AVAILABLE', 'APPROVED', 'CLOSED_NOT_AVAILABLE', 'CANCELLED')),
    times_rejected      INT          NOT NULL DEFAULT 0,
    cancel_reason       TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    closed_at           TIMESTAMPTZ,
    UNIQUE (tenant_id, reference)
);
CREATE INDEX idx_audit_evreq_tenant ON audit_evidence_requests (tenant_id, status);
CREATE INDEX idx_audit_evreq_engagement ON audit_evidence_requests (engagement_id);

CREATE TABLE audit_evidence_request_rounds (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL,
    request_id     UUID         NOT NULL REFERENCES audit_evidence_requests (id),
    round_no       INT          NOT NULL,
    kind           VARCHAR(14)  NOT NULL CHECK (kind IN ('SUBMISSION', 'NOT_AVAILABLE')),
    note           TEXT         NOT NULL,
    submitted_by   UUID         NOT NULL,
    submitted_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    decision       VARCHAR(10)  CHECK (decision IN ('APPROVED', 'REJECTED')),
    decision_note  TEXT,
    decided_by     UUID,
    decided_at     TIMESTAMPTZ,
    UNIQUE (request_id, round_no)
);

CREATE TABLE audit_evidence_request_round_files (
    round_id    UUID NOT NULL REFERENCES audit_evidence_request_rounds (id),
    evidence_id UUID NOT NULL,
    PRIMARY KEY (round_id, evidence_id)
);

CREATE TABLE audit_evidence_request_comments (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL,
    request_id    UUID         NOT NULL REFERENCES audit_evidence_requests (id),
    author_kind   VARCHAR(8)   NOT NULL CHECK (author_kind IN ('STAFF', 'AUDITOR')),
    author_id     UUID         NOT NULL,
    author_label  VARCHAR(255),
    body          TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_evreq_comments ON audit_evidence_request_comments (request_id, created_at);
