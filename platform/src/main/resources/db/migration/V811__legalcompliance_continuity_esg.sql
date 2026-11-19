-- ============================================================================
-- Legal / Compliance — Phase 6 (part 1): business continuity and ESG registers. Depends on V294. Confirm the next free Flyway version before applying.
--
-- Business continuity: critical processes (with recovery time/point objectives), continuity plans (one approved plan per process; author and approver differ),
-- and append-only exercises. "Overdue" test, plan review and BIA review are derived, never stored.
-- ESG: metrics and append-only readings. A reading keeps the target and direction that applied when it was recorded, so past "met / missed" results
-- never change if the target is edited later. Corrections supersede the earlier reading (kept, with a reason) instead of overwriting it.
-- ============================================================================

CREATE TABLE legalcompliance_bc_processes (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    reference        VARCHAR(20) NOT NULL,
    name             VARCHAR(255) NOT NULL,
    description      TEXT,
    owner_name       VARCHAR(255) NOT NULL,
    department       VARCHAR(100),
    criticality      VARCHAR(8) NOT NULL,
    rto_hours        INTEGER NOT NULL,
    rpo_hours        INTEGER NOT NULL,
    dependencies     TEXT,
    status           VARCHAR(8) NOT NULL DEFAULT 'ACTIVE',
    bia_reviewed_on  DATE,
    bia_review_months INTEGER NOT NULL DEFAULT 12,
    retired_reason   TEXT,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_bcp_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_lc_bcp_crit CHECK (criticality IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_lc_bcp_status CHECK (status IN ('ACTIVE','RETIRED')),
    CONSTRAINT ck_lc_bcp_rto CHECK (rto_hours BETWEEN 1 AND 8760),
    CONSTRAINT ck_lc_bcp_rpo CHECK (rpo_hours BETWEEN 0 AND 8760),
    CONSTRAINT ck_lc_bcp_bia CHECK (bia_review_months BETWEEN 1 AND 36)
);
CREATE INDEX idx_lc_bcp_tenant ON legalcompliance_bc_processes (tenant_id, status);

CREATE TABLE legalcompliance_bc_plans (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    reference           VARCHAR(20) NOT NULL,
    process_id          UUID NOT NULL REFERENCES legalcompliance_bc_processes (id),
    title               VARCHAR(255) NOT NULL,
    version_label       VARCHAR(20) NOT NULL,
    owner_name          VARCHAR(255) NOT NULL,
    content             TEXT NOT NULL,
    status              VARCHAR(8) NOT NULL DEFAULT 'DRAFT',
    review_months       INTEGER NOT NULL DEFAULT 12,
    author_user_id      UUID NOT NULL,
    author_name         VARCHAR(255) NOT NULL,
    approved_by_user_id UUID,
    approved_by_name    VARCHAR(255),
    approved_on         DATE,
    next_review_due     DATE,
    last_reviewed_on    DATE,
    retired_reason      TEXT,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_bcpl_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_lc_bcpl_status CHECK (status IN ('DRAFT','APPROVED','RETIRED')),
    CONSTRAINT ck_lc_bcpl_review CHECK (review_months BETWEEN 1 AND 36),
    CONSTRAINT ck_lc_bcpl_approved CHECK (status <> 'APPROVED' OR (approved_by_user_id IS NOT NULL AND approved_on IS NOT NULL AND next_review_due IS NOT NULL)),
    CONSTRAINT ck_lc_bcpl_segregation CHECK (approved_by_user_id IS NULL OR approved_by_user_id <> author_user_id)
);
CREATE INDEX idx_lc_bcpl_process ON legalcompliance_bc_plans (tenant_id, process_id);
CREATE UNIQUE INDEX uq_lc_bcpl_one_approved ON legalcompliance_bc_plans (process_id) WHERE status = 'APPROVED';

CREATE TABLE legalcompliance_bc_tests (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    plan_id            UUID NOT NULL REFERENCES legalcompliance_bc_plans (id),
    test_date          DATE NOT NULL,
    test_type          VARCHAR(12) NOT NULL,
    outcome            VARCHAR(18) NOT NULL,
    rto_target_hours   INTEGER NOT NULL,
    achieved_rto_hours NUMERIC(8,2),
    issues             TEXT,
    actions            TEXT,
    conducted_by_name  VARCHAR(255) NOT NULL,
    recorded_by_user_id UUID NOT NULL,
    recorded_by_name   VARCHAR(255) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_lc_bct_type CHECK (test_type IN ('TABLETOP','WALKTHROUGH','SIMULATION','FAILOVER')),
    CONSTRAINT ck_lc_bct_outcome CHECK (outcome IN ('PASSED','PASSED_WITH_ISSUES','FAILED')),
    CONSTRAINT ck_lc_bct_rto CHECK (achieved_rto_hours IS NULL OR achieved_rto_hours >= 0)
);
CREATE INDEX idx_lc_bct_plan ON legalcompliance_bc_tests (tenant_id, plan_id, test_date DESC);

CREATE TABLE legalcompliance_esg_metrics (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    reference      VARCHAR(20) NOT NULL,
    name           VARCHAR(255) NOT NULL,
    description    TEXT,
    category       VARCHAR(13) NOT NULL,
    unit           VARCHAR(40) NOT NULL,
    frequency      VARCHAR(10) NOT NULL,
    direction      VARCHAR(16) NOT NULL,
    target_value   NUMERIC(18,4),
    owner_name     VARCHAR(255) NOT NULL,
    framework_ref  VARCHAR(100),
    status         VARCHAR(8) NOT NULL DEFAULT 'ACTIVE',
    retired_reason TEXT,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_esgm_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_lc_esgm_cat CHECK (category IN ('ENVIRONMENTAL','SOCIAL','GOVERNANCE')),
    CONSTRAINT ck_lc_esgm_freq CHECK (frequency IN ('MONTHLY','QUARTERLY','ANNUALLY')),
    CONSTRAINT ck_lc_esgm_dir CHECK (direction IN ('LOWER_IS_BETTER','HIGHER_IS_BETTER','TRACK_ONLY')),
    CONSTRAINT ck_lc_esgm_status CHECK (status IN ('ACTIVE','RETIRED')),
    CONSTRAINT ck_lc_esgm_target CHECK ((direction = 'TRACK_ONLY') = (target_value IS NULL))
);
CREATE INDEX idx_lc_esgm_tenant ON legalcompliance_esg_metrics (tenant_id, status);

CREATE TABLE legalcompliance_esg_readings (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    metric_id           UUID NOT NULL REFERENCES legalcompliance_esg_metrics (id),
    period_start        DATE NOT NULL,
    period_end          DATE NOT NULL,
    reading_value       NUMERIC(18,4) NOT NULL,
    notes               TEXT,
    target_snapshot     NUMERIC(18,4),
    direction_snapshot  VARCHAR(16) NOT NULL,
    recorded_by_user_id UUID NOT NULL,
    recorded_by_name    VARCHAR(255) NOT NULL,
    corrects_id         UUID,
    superseded_at       TIMESTAMPTZ,
    supersede_reason    TEXT,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_lc_esgr_period CHECK (period_end >= period_start),
    CONSTRAINT ck_lc_esgr_dir CHECK (direction_snapshot IN ('LOWER_IS_BETTER','HIGHER_IS_BETTER','TRACK_ONLY'))
);
CREATE INDEX idx_lc_esgr_metric ON legalcompliance_esg_readings (tenant_id, metric_id, period_end DESC);
CREATE UNIQUE INDEX uq_lc_esgr_period ON legalcompliance_esg_readings (metric_id, period_end) WHERE superseded_at IS NULL;

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'LEGALCOMPLIANCE_BCM_MANAGE', 'Manage business continuity processes, plans and exercises'),
    (gen_random_uuid(), 'LEGALCOMPLIANCE_ESG_MANAGE', 'Manage ESG metrics and record readings')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('LEGALCOMPLIANCE_BCM_MANAGE','LEGALCOMPLIANCE_ESG_MANAGE')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
