-- ============================================================================
-- Legal / Compliance — Phase 3: audits, audit findings and corrective / preventive actions (CAPA).
-- Depends on V287. Confirm the next free Flyway version before applying.
--
--  * Audit findings are a separate table from control-test findings (an audit raises many findings; a test raises one).
--  * A CAPA hangs off exactly one finding of either kind (two nullable FKs, exactly one set).
--  * References come from the shared legalcompliance_ref_seq (AUD- / AFN- / CAPA-): unique per tenant, not contiguous.
-- ============================================================================

CREATE TABLE legalcompliance_audits (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    reference          VARCHAR(20) NOT NULL,
    title              VARCHAR(255) NOT NULL,
    audit_type         VARCHAR(10) NOT NULL,
    scope              TEXT NOT NULL,
    objectives         TEXT NOT NULL,
    work_programme     TEXT,
    auditor_id         UUID,
    lead_auditor_name  VARCHAR(255) NOT NULL,
    auditing_firm      VARCHAR(255),
    planned_start      DATE NOT NULL,
    planned_end        DATE NOT NULL,
    status             VARCHAR(12) NOT NULL,
    control_ids        JSONB NOT NULL DEFAULT '[]'::jsonb,
    started_at         TIMESTAMPTZ,
    overall_rating     VARCHAR(20),
    conclusion         TEXT,
    completed_at       TIMESTAMPTZ,
    completed_by_name  VARCHAR(255),
    cancel_reason      TEXT,
    created_by         UUID,
    created_by_name    VARCHAR(255),
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_audit_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_lc_audit_type CHECK (audit_type IN ('INTERNAL','EXTERNAL')),
    CONSTRAINT ck_lc_audit_status CHECK (status IN ('PLANNED','FIELDWORK','REPORTING','COMPLETED','CANCELLED')),
    CONSTRAINT ck_lc_audit_rating CHECK (overall_rating IS NULL OR overall_rating IN ('SATISFACTORY','NEEDS_IMPROVEMENT','UNSATISFACTORY')),
    CONSTRAINT ck_lc_audit_dates CHECK (planned_end >= planned_start),
    CONSTRAINT ck_lc_audit_done CHECK (status <> 'COMPLETED' OR (overall_rating IS NOT NULL AND conclusion IS NOT NULL))
);
CREATE INDEX idx_lc_audit_tenant ON legalcompliance_audits (tenant_id, status, planned_start);

CREATE TABLE legalcompliance_audit_findings (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    reference           VARCHAR(20) NOT NULL,
    audit_id            UUID NOT NULL REFERENCES legalcompliance_audits (id),
    control_id          UUID REFERENCES legalcompliance_controls (id),
    finding_type        VARCHAR(20) NOT NULL,
    title               VARCHAR(255) NOT NULL,
    description         TEXT NOT NULL,
    root_cause          TEXT,
    severity            VARCHAR(10) NOT NULL,
    status              VARCHAR(15) NOT NULL,
    owner_id            UUID,
    owner_name          VARCHAR(255) NOT NULL,
    due_date            DATE NOT NULL,
    management_response TEXT,
    responded_by        UUID,
    responded_by_name   VARCHAR(255),
    responded_at        TIMESTAMPTZ,
    closed_by           UUID,
    closed_by_name      VARCHAR(255),
    closed_at           TIMESTAMPTZ,
    closure_note        TEXT,
    created_by          UUID,
    created_by_name     VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_afinding_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_lc_afinding_type CHECK (finding_type IN ('NON_CONFORMANCE','DEFICIENCY','BREACH','OBSERVATION')),
    CONSTRAINT ck_lc_afinding_sev CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_lc_afinding_status CHECK (status IN ('OPEN','IN_REMEDIATION','CLOSED'))
);
CREATE INDEX idx_lc_afinding_audit ON legalcompliance_audit_findings (tenant_id, audit_id);
CREATE INDEX idx_lc_afinding_due ON legalcompliance_audit_findings (due_date) WHERE status <> 'CLOSED';

CREATE TABLE legalcompliance_capas (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    reference          VARCHAR(20) NOT NULL,
    finding_id         UUID REFERENCES legalcompliance_findings (id),
    audit_finding_id   UUID REFERENCES legalcompliance_audit_findings (id),
    capa_type          VARCHAR(10) NOT NULL,
    title              VARCHAR(255) NOT NULL,
    action_plan        TEXT NOT NULL,
    root_cause         TEXT NOT NULL,
    owner_id           UUID,
    owner_name         VARCHAR(255) NOT NULL,
    due_date           DATE NOT NULL,
    status             VARCHAR(12) NOT NULL,
    approved_by        UUID,
    approved_by_name   VARCHAR(255),
    approved_at        TIMESTAMPTZ,
    approval_note      TEXT,
    returned_count     INTEGER NOT NULL DEFAULT 0,
    started_at         TIMESTAMPTZ,
    completed_by       UUID,
    completed_by_name  VARCHAR(255),
    completed_at       TIMESTAMPTZ,
    completion_note    TEXT,
    evidence_snapshot  JSONB NOT NULL DEFAULT '[]'::jsonb,
    verified_by        UUID,
    verified_by_name   VARCHAR(255),
    verified_at        TIMESTAMPTZ,
    verification_note  TEXT,
    reopened_count     INTEGER NOT NULL DEFAULT 0,
    cancel_reason      TEXT,
    created_by         UUID,
    created_by_name    VARCHAR(255),
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_capa_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_lc_capa_type CHECK (capa_type IN ('CORRECTIVE','PREVENTIVE')),
    CONSTRAINT ck_lc_capa_status CHECK (status IN ('PROPOSED','APPROVED','IN_PROGRESS','COMPLETED','VERIFIED','CANCELLED')),
    CONSTRAINT ck_lc_capa_one_parent CHECK ((finding_id IS NOT NULL AND audit_finding_id IS NULL) OR (finding_id IS NULL AND audit_finding_id IS NOT NULL))
);
CREATE INDEX idx_lc_capa_tenant ON legalcompliance_capas (tenant_id, status);
CREATE INDEX idx_lc_capa_finding ON legalcompliance_capas (finding_id) WHERE finding_id IS NOT NULL;
CREATE INDEX idx_lc_capa_afinding ON legalcompliance_capas (audit_finding_id) WHERE audit_finding_id IS NOT NULL;
CREATE INDEX idx_lc_capa_due ON legalcompliance_capas (due_date) WHERE status NOT IN ('VERIFIED','CANCELLED');

-- Audit findings are raised and closed with the existing FINDING_MANAGE / FINDING_VERIFY permissions; these two are new.
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'LEGALCOMPLIANCE_AUDIT_MANAGE', 'Plan and run audits and raise audit findings'),
    (gen_random_uuid(), 'LEGALCOMPLIANCE_CAPA_APPROVE', 'Approve or return corrective and preventive actions; cannot approve your own')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('LEGALCOMPLIANCE_AUDIT_MANAGE','LEGALCOMPLIANCE_CAPA_APPROVE')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
