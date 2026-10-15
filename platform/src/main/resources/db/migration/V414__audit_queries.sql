-- A13. Audit queries and external review notes raised by an external auditor. They live apart from internal audit findings; a
-- business can explicitly link one to an internal finding, and that link changes nothing about what the auditor can see.
CREATE TABLE audit_queries (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID         NOT NULL,
    engagement_id     UUID REFERENCES external_audit_engagements (id),
    kind              VARCHAR(12)  NOT NULL CHECK (kind IN ('QUERY', 'REVIEW_NOTE')),
    reference         VARCHAR(20)  NOT NULL,
    title             VARCHAR(200) NOT NULL,
    subject_type      VARCHAR(20)  CHECK (subject_type IN ('JOURNAL_ENTRY', 'INVOICE', 'BILL', 'PAYMENT', 'EVIDENCE', 'EVIDENCE_REQUEST', 'ACCOUNT', 'OTHER')),
    subject_id        UUID,
    subject_label     VARCHAR(200),
    due_date          DATE,
    raised_by         UUID         NOT NULL,
    raised_by_label   VARCHAR(255),
    status            VARCHAR(10)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'ANSWERED', 'RESOLVED', 'WITHDRAWN')),
    times_reopened    INT          NOT NULL DEFAULT 0,
    linked_finding_id UUID,
    linked_by         UUID,
    linked_at         TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    closed_at         TIMESTAMPTZ,
    UNIQUE (tenant_id, reference)
);
CREATE INDEX idx_audit_queries_tenant ON audit_queries (tenant_id, status);
CREATE INDEX idx_audit_queries_finding ON audit_queries (tenant_id, linked_finding_id);

CREATE TABLE audit_query_messages (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL,
    query_id      UUID         NOT NULL REFERENCES audit_queries (id),
    author_kind   VARCHAR(8)   NOT NULL CHECK (author_kind IN ('STAFF', 'AUDITOR')),
    author_id     UUID         NOT NULL,
    author_label  VARCHAR(255),
    message_kind  VARCHAR(10)  NOT NULL CHECK (message_kind IN ('QUESTION', 'ANSWER', 'FOLLOW_UP', 'RESOLUTION', 'WITHDRAWAL')),
    body          TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_query_messages ON audit_query_messages (query_id, created_at);
