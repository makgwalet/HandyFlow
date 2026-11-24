-- Compliance Services: assessments. An assessment judges every obligation assigned to a client on one date.
-- Evidence is checked by the shared readiness evaluator; whether the control is actually in place is a
-- person's judgement. Having a document is never enough to count as compliant. A completed assessment is frozen.

CREATE TABLE IF NOT EXISTS compliance_assessment_weights (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    w_low        INTEGER NOT NULL DEFAULT 1,
    w_medium     INTEGER NOT NULL DEFAULT 2,
    w_high       INTEGER NOT NULL DEFAULT 4,
    w_critical   INTEGER NOT NULL DEFAULT 8,
    updated_by   UUID,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_compliance_assessment_weights UNIQUE (tenant_id),
    CONSTRAINT ck_assessment_weights CHECK (w_low BETWEEN 1 AND 100 AND w_medium BETWEEN 1 AND 100 AND w_high BETWEEN 1 AND 100 AND w_critical BETWEEN 1 AND 100)
);

CREATE TABLE IF NOT EXISTS compliance_assessments (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL REFERENCES compliance_service_clients(id) ON DELETE CASCADE,
    status          VARCHAR(10) NOT NULL DEFAULT 'DRAFT',     -- DRAFT, COMPLETED
    as_of           DATE NOT NULL,
    w_low           INTEGER NOT NULL,
    w_medium        INTEGER NOT NULL,
    w_high          INTEGER NOT NULL,
    w_critical      INTEGER NOT NULL,
    score_percent   NUMERIC(5,1),
    scored_count    INTEGER,
    unscored_count  INTEGER,
    summary_note    TEXT,
    started_by      UUID,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_by    UUID,
    completed_at    TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_assessment_completed CHECK (status <> 'COMPLETED' OR completed_at IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_compliance_assessments ON compliance_assessments(tenant_id, client_id);
-- one assessment in progress per client
CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_assessment_draft ON compliance_assessments(client_id) WHERE status = 'DRAFT';

CREATE TABLE IF NOT EXISTS compliance_assessment_items (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    assessment_id    UUID NOT NULL REFERENCES compliance_assessments(id) ON DELETE CASCADE,
    obligation_id    UUID NOT NULL,
    version_id       UUID NOT NULL,
    version_number   INTEGER NOT NULL,
    code             VARCHAR(60) NOT NULL,
    name             VARCHAR(255),
    risk_level       VARCHAR(10),
    applicability    VARCHAR(15) NOT NULL,                    -- APPLICABLE, NOT_APPLICABLE, PENDING
    evidence_status  VARCHAR(12) NOT NULL,                    -- NOT_REQUIRED, MET, MISSING, EXPIRED, PENDING
    control          VARCHAR(10) NOT NULL DEFAULT 'UNKNOWN',  -- UNKNOWN, YES, PARTIAL, NO
    workflow         VARCHAR(12) NOT NULL DEFAULT 'NONE',     -- NONE, REMEDIATION, AUTHORITY
    status           VARCHAR(24) NOT NULL,
    note             TEXT,
    assessed_by      UUID,
    assessed_at      TIMESTAMPTZ,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_compliance_assessment_item UNIQUE (assessment_id, obligation_id)
);
CREATE INDEX IF NOT EXISTS idx_compliance_assessment_items ON compliance_assessment_items(tenant_id, assessment_id);

CREATE TABLE IF NOT EXISTS compliance_assessment_evidence (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    item_id        UUID NOT NULL REFERENCES compliance_assessment_items(id) ON DELETE CASCADE,
    document_type  VARCHAR(120) NOT NULL,
    mandatory      BOOLEAN NOT NULL,
    result         VARCHAR(14) NOT NULL,
    detail         TEXT,
    expires_on     DATE
);
CREATE INDEX IF NOT EXISTS idx_compliance_assessment_evidence ON compliance_assessment_evidence(tenant_id, item_id);
