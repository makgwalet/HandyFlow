-- Compliance Services: the practice's own service catalogue. A service has numbered versions. A version is written
-- as a draft, reviewed by a second person and, once approved, never changed: a change is a new version.
-- No prices live here. A service only says which pricing model and billing trigger it uses; the price book comes later.

CREATE TABLE IF NOT EXISTS compliance_service_definitions (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    code        VARCHAR(60) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    version     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_compliance_service_code UNIQUE (tenant_id, code)
);

CREATE TABLE IF NOT EXISTS compliance_service_versions (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    service_id        UUID NOT NULL REFERENCES compliance_service_definitions(id) ON DELETE CASCADE,
    version_number    INTEGER NOT NULL,
    review_status     VARCHAR(12) NOT NULL DEFAULT 'DRAFT',     -- DRAFT, IN_REVIEW, APPROVED
    name              VARCHAR(255),
    category          VARCHAR(20),
    description       TEXT,
    applicability_note TEXT,
    exclusions        TEXT,
    estimated_hours   NUMERIC(7,2),
    sla_days          INTEGER,
    responsible_role  VARCHAR(20),
    requires_review   BOOLEAN NOT NULL DEFAULT TRUE,
    pricing_model     VARCHAR(20),
    billing_trigger   VARCHAR(20),
    change_summary    TEXT,
    authored_by       UUID,
    submitted_by      UUID,
    submitted_at      TIMESTAMPTZ,
    reviewed_by       UUID,
    reviewed_at       TIMESTAMPTZ,
    review_note       TEXT,
    review_exception_reason TEXT,
    effective_from    DATE,
    effective_to      DATE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_compliance_service_version UNIQUE (service_id, version_number),
    CONSTRAINT ck_service_review_status CHECK (review_status IN ('DRAFT', 'IN_REVIEW', 'APPROVED')),
    CONSTRAINT ck_service_effective CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_service_approved_dated CHECK (review_status <> 'APPROVED' OR effective_from IS NOT NULL),
    CONSTRAINT ck_service_hours CHECK (estimated_hours IS NULL OR (estimated_hours > 0 AND estimated_hours <= 2000)),
    CONSTRAINT ck_service_sla CHECK (sla_days IS NULL OR (sla_days >= 1 AND sla_days <= 365))
);
CREATE INDEX IF NOT EXISTS idx_compliance_service_versions ON compliance_service_versions(tenant_id, service_id);
-- one open draft or review per service
CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_service_open_draft
    ON compliance_service_versions(service_id) WHERE review_status IN ('DRAFT', 'IN_REVIEW');

CREATE TABLE IF NOT EXISTS compliance_service_items (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    version_id  UUID NOT NULL REFERENCES compliance_service_versions(id) ON DELETE CASCADE,
    kind        VARCHAR(12) NOT NULL,            -- DELIVERABLE, CHECKLIST, DOCUMENT
    text        VARCHAR(300) NOT NULL,
    mandatory   BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_service_item_kind CHECK (kind IN ('DELIVERABLE', 'CHECKLIST', 'DOCUMENT'))
);
CREATE INDEX IF NOT EXISTS idx_compliance_service_items ON compliance_service_items(version_id);
