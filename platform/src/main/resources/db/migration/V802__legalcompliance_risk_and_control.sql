-- ============================================================================
-- Legal / Compliance — Phase 2, part 1: enterprise risk register, control catalogue and the
-- many-to-many Obligation <-> Risk <-> Control mapping. Depends on V284/V285 only for the
-- permissions pattern; confirm the next free Flyway version before applying.
--
--  * Risk assessments are APPEND-ONLY: a new assessment supersedes the old one, which stays readable.
--    Each row stores the scores, ratings and the appetite/tolerance that applied, so a historic
--    score can be reproduced after the settings change.
--  * Controls start with operating_effectiveness = NOT_TESTED. Nothing in this migration (or this
--    chunk's code) can set another value: effectiveness will only come from reviewed control tests.
-- ============================================================================

CREATE SEQUENCE legalcompliance_ref_seq START 1;

CREATE TABLE legalcompliance_risk_settings (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    appetite_max   INTEGER NOT NULL DEFAULT 9,
    tolerance_max  INTEGER NOT NULL DEFAULT 16,
    updated_by     UUID,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_risk_settings_tenant UNIQUE (tenant_id),
    CONSTRAINT ck_lc_risk_settings CHECK (appetite_max >= 1 AND appetite_max < tolerance_max AND tolerance_max <= 25)
);

CREATE TABLE legalcompliance_risks (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    reference           VARCHAR(20) NOT NULL,
    title               VARCHAR(255) NOT NULL,
    description         TEXT,
    category            VARCHAR(20) NOT NULL,
    status              VARCHAR(12) NOT NULL,
    emerging            BOOLEAN NOT NULL DEFAULT FALSE,
    owner_id            UUID,
    owner_name          VARCHAR(255) NOT NULL,
    next_review_date    DATE NOT NULL,
    treatment_strategy  VARCHAR(10),
    treatment_plan      TEXT,
    treatment_due       DATE,
    accepted_by         UUID,
    accepted_by_name    VARCHAR(255),
    accepted_at         TIMESTAMPTZ,
    acceptance_reason   TEXT,
    acceptance_expires  DATE,
    closed_at           TIMESTAMPTZ,
    close_reason        TEXT,
    created_by          UUID,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_risk_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_lc_risk_category CHECK (category IN ('STRATEGIC','OPERATIONAL','FINANCIAL','LEGAL','COMPLIANCE','REPUTATIONAL','TECHNOLOGY')),
    CONSTRAINT ck_lc_risk_status CHECK (status IN ('IDENTIFIED','ASSESSED','TREATING','ACCEPTED','CLOSED')),
    CONSTRAINT ck_lc_risk_strategy CHECK (treatment_strategy IS NULL OR treatment_strategy IN ('MITIGATE','TRANSFER','AVOID'))
);
CREATE INDEX idx_lc_risk_tenant ON legalcompliance_risks (tenant_id, status);
CREATE INDEX idx_lc_risk_review ON legalcompliance_risks (next_review_date) WHERE status <> 'CLOSED';

CREATE TABLE legalcompliance_risk_assessments (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    risk_id           UUID NOT NULL REFERENCES legalcompliance_risks (id),
    inherent_likelihood INTEGER NOT NULL,
    inherent_impact     INTEGER NOT NULL,
    residual_likelihood INTEGER NOT NULL,
    residual_impact     INTEGER NOT NULL,
    inherent_score    INTEGER NOT NULL,
    residual_score    INTEGER NOT NULL,
    inherent_rating   VARCHAR(10) NOT NULL,
    residual_rating   VARCHAR(10) NOT NULL,
    appetite_max      INTEGER NOT NULL,
    tolerance_max     INTEGER NOT NULL,
    rationale         TEXT NOT NULL,
    assessed_by       UUID,
    assessed_by_name  VARCHAR(255),
    assessed_at       TIMESTAMPTZ NOT NULL,
    is_current        BOOLEAN NOT NULL DEFAULT TRUE,
    superseded_at     TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_lc_ra_scale CHECK (inherent_likelihood BETWEEN 1 AND 5 AND inherent_impact BETWEEN 1 AND 5
        AND residual_likelihood BETWEEN 1 AND 5 AND residual_impact BETWEEN 1 AND 5),
    CONSTRAINT ck_lc_ra_residual CHECK (residual_score <= inherent_score)
);
CREATE UNIQUE INDEX uq_lc_ra_one_current ON legalcompliance_risk_assessments (risk_id) WHERE is_current;
CREATE INDEX idx_lc_ra_risk ON legalcompliance_risk_assessments (risk_id, assessed_at DESC);

CREATE TABLE legalcompliance_controls (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    reference               VARCHAR(20) NOT NULL,
    title                   VARCHAR(255) NOT NULL,
    objective               TEXT,
    procedure_text          TEXT,
    control_type            VARCHAR(12) NOT NULL,
    nature                  VARCHAR(10) NOT NULL,
    frequency               VARCHAR(12) NOT NULL,
    owner_id                UUID,
    owner_name              VARCHAR(255) NOT NULL,
    expected_evidence       TEXT,
    status                  VARCHAR(10) NOT NULL,
    design_rating           VARCHAR(20) NOT NULL DEFAULT 'NOT_ASSESSED',
    design_assessed_by      UUID,
    design_assessed_by_name VARCHAR(255),
    design_assessed_at      TIMESTAMPTZ,
    design_note             TEXT,
    operating_effectiveness VARCHAR(20) NOT NULL DEFAULT 'NOT_TESTED',
    last_tested_at          TIMESTAMPTZ,
    activated_at            TIMESTAMPTZ,
    retired_at              TIMESTAMPTZ,
    retire_reason           TEXT,
    created_by              UUID,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_control_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_lc_control_type CHECK (control_type IN ('PREVENTIVE','DETECTIVE','CORRECTIVE')),
    CONSTRAINT ck_lc_control_nature CHECK (nature IN ('MANUAL','AUTOMATED','HYBRID')),
    CONSTRAINT ck_lc_control_freq CHECK (frequency IN ('CONTINUOUS','DAILY','WEEKLY','MONTHLY','QUARTERLY','ANNUALLY','AD_HOC')),
    CONSTRAINT ck_lc_control_status CHECK (status IN ('DRAFT','ACTIVE','RETIRED')),
    CONSTRAINT ck_lc_control_design CHECK (design_rating IN ('NOT_ASSESSED','ADEQUATE','NEEDS_IMPROVEMENT','INADEQUATE')),
    CONSTRAINT ck_lc_control_eff CHECK (operating_effectiveness IN ('NOT_TESTED','EFFECTIVE','PARTIALLY_EFFECTIVE','INEFFECTIVE'))
);
CREATE INDEX idx_lc_control_tenant ON legalcompliance_controls (tenant_id, status);

-- One table for all three many-to-many relationships. Targets live in different tables, so ids are validated in the
-- service rather than by foreign key. Unlinking deletes the row and writes an audit event.
CREATE TABLE legalcompliance_grc_links (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    link_type        VARCHAR(20) NOT NULL,
    source_id        UUID NOT NULL,
    target_id        UUID NOT NULL,
    note             VARCHAR(500),
    created_by       UUID,
    created_by_name  VARCHAR(255),
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_grc_link UNIQUE (tenant_id, link_type, source_id, target_id),
    CONSTRAINT ck_lc_grc_type CHECK (link_type IN ('RISK_OBLIGATION','RISK_CONTROL','CONTROL_OBLIGATION'))
);
CREATE INDEX idx_lc_grc_source ON legalcompliance_grc_links (tenant_id, link_type, source_id);
CREATE INDEX idx_lc_grc_target ON legalcompliance_grc_links (tenant_id, link_type, target_id);

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'LEGALCOMPLIANCE_RISK_MANAGE',    'Create, assess and treat risks; link risks to obligations and controls'),
    (gen_random_uuid(), 'LEGALCOMPLIANCE_RISK_ACCEPT',    'Formally accept a risk within tolerance'),
    (gen_random_uuid(), 'LEGALCOMPLIANCE_CONTROL_MANAGE', 'Create, activate, retire and link controls'),
    (gen_random_uuid(), 'LEGALCOMPLIANCE_CONTROL_ASSESS', 'Assess the design adequacy of a control')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('LEGALCOMPLIANCE_RISK_MANAGE','LEGALCOMPLIANCE_RISK_ACCEPT','LEGALCOMPLIANCE_CONTROL_MANAGE','LEGALCOMPLIANCE_CONTROL_ASSESS')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
