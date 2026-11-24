-- Compliance Services: the obligation catalogue. Each obligation has numbered versions. A version is
-- written as a draft, reviewed, and once approved it is never changed: a change is a new version.
-- Old assessments keep pointing at the version that applied to them.

CREATE TABLE IF NOT EXISTS compliance_obligations (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    code        VARCHAR(60) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_compliance_obligation_code UNIQUE (tenant_id, code)
);

CREATE TABLE IF NOT EXISTS compliance_obligation_versions (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    obligation_id    UUID NOT NULL REFERENCES compliance_obligations(id) ON DELETE CASCADE,
    version_number   INTEGER NOT NULL,
    review_status    VARCHAR(12) NOT NULL DEFAULT 'DRAFT',      -- DRAFT, IN_REVIEW, APPROVED
    name             VARCHAR(255),
    description      TEXT,
    legal_source     VARCHAR(500),
    regulator        VARCHAR(200),
    jurisdiction     VARCHAR(40) NOT NULL DEFAULT 'ZA',
    frequency        VARCHAR(12),                                -- ONCE, MONTHLY, QUARTERLY, BIANNUAL, ANNUAL, ON_EXPIRY, ON_EVENT
    due_rule_type    VARCHAR(20) NOT NULL DEFAULT 'NONE',
    due_month        INTEGER,
    due_day          INTEGER,
    due_offset_days  INTEGER,
    due_offset_months INTEGER,
    validity_months  INTEGER,
    risk_level       VARCHAR(10),                                -- LOW, MEDIUM, HIGH, CRITICAL
    responsible_role VARCHAR(20),                                -- CONSULTANT, REVIEWER, CLIENT, EXTERNAL_PROVIDER
    guidance         TEXT,
    change_summary   TEXT,
    authored_by      UUID,
    submitted_by     UUID,
    submitted_at     TIMESTAMPTZ,
    reviewed_by      UUID,
    reviewed_at      TIMESTAMPTZ,
    review_note      TEXT,
    review_exception_reason TEXT,
    effective_from   DATE,
    effective_to     DATE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_compliance_obligation_version UNIQUE (obligation_id, version_number),
    CONSTRAINT ck_obligation_effective CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_obligation_approved_dated CHECK (review_status <> 'APPROVED' OR effective_from IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_compliance_obligation_versions ON compliance_obligation_versions(tenant_id, obligation_id);
-- one open draft or review per obligation
CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_obligation_open_draft
    ON compliance_obligation_versions(obligation_id) WHERE review_status IN ('DRAFT', 'IN_REVIEW');

CREATE TABLE IF NOT EXISTS compliance_obligation_conditions (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    version_id  UUID NOT NULL REFERENCES compliance_obligation_versions(id) ON DELETE CASCADE,
    field_name  VARCHAR(20) NOT NULL,     -- ENTITY_TYPE, INDUSTRY, EMPLOYEE_COUNT, TURNOVER_BAND, VAT_REGISTERED
    operator    VARCHAR(10) NOT NULL,     -- IN, GTE, LTE, IS_TRUE, IS_FALSE
    value       VARCHAR(500),
    sort_order  INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_compliance_obligation_conditions ON compliance_obligation_conditions(version_id);

CREATE TABLE IF NOT EXISTS compliance_obligation_evidence (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    version_id    UUID NOT NULL REFERENCES compliance_obligation_versions(id) ON DELETE CASCADE,
    document_type VARCHAR(120) NOT NULL,
    mandatory     BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order    INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_compliance_obligation_evidence ON compliance_obligation_evidence(version_id);
