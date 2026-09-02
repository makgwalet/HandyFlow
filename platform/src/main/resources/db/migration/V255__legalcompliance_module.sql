-- ============================================================================
-- Legal / Compliance (internal) module — baseline schema + catalogue seed.
--
-- *** VERSION NUMBER NOT CONFIRMED — READ BEFORE APPLYING ***
-- V255 is chosen because the most recent version number seen anywhere in
-- this engagement (across this session and the handoff from the prior one)
-- is V254 (tenant_module_discount, itself flagged unverified against the
-- real flyway_schema_history in that same handoff). This file has NOT been
-- checked against the live flyway_schema_history table — confirm the next
-- free version number there and rename this file accordingly before it is
-- applied. Same caution every migration in this engagement has carried.
--
-- *** MODULE CATALOGUE / PERMISSION SEED SHAPE — ALSO FLAGGED ***
-- The INSERT statements below mirror the exact logic confirmed in
-- AdminLookupService.createModule() (module_catalogue columns, the
-- {KEY}_READ/{KEY}_MANAGE/{KEY}_ADMIN permission-generation pattern, and
-- granting all three to every ADMIN role) — but that method is the path
-- for a tenant admin adding a module at runtime via POST /admin/modules,
-- not the original seed for a baseline platform module. I could not
-- locate the actual original seed migration for an existing baseline
-- module (e.g. whichever migration first inserted 'crm'/'security'/
-- 'payrollbureau' into module_catalogue) in what's synced to this
-- session, so this migration's shape is inferred from confirmed-correct
-- logic rather than copied from a directly-verified precedent. Worth a
-- diff against that original seed migration, if it can be located,
-- before this ships — flagging explicitly rather than presenting this as
-- independently confirmed.
--
-- Module key is lowercase ("legalcompliance", no separator) to match the
-- confirmed convention every other baseline module's
-- featureGuard.requireModule(...) call actually uses (crm, security, hr,
-- payrollbureau, contracting) — NOT the UPPER_SNAKE_CASE the admin-add
-- endpoint enforces for its own dynamically-created modules. Permission
-- names are uppercase (LEGALCOMPLIANCE_READ/_MANAGE/_ADMIN), matching
-- payrollbureau's own confirmed real pattern (key="payrollbureau",
-- permissions="PAYROLLBUREAU_*") exactly.
-- ============================================================================

-- ── Regulatory obligation tracker ──────────────────────────────────────────

CREATE TABLE legalcompliance_regulatory_obligations (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    title                   VARCHAR(255) NOT NULL,
    category                VARCHAR(30) NOT NULL,
    regulation_reference    VARCHAR(255),
    description             TEXT,
    responsible_user_id     UUID,
    responsible_user_name   VARCHAR(255),
    review_date             DATE NOT NULL,
    recurrence              VARCHAR(20) NOT NULL,
    status                  VARCHAR(20) NOT NULL,
    linked_contract_id      UUID,
    notes                   TEXT,
    last_reviewed_at        TIMESTAMPTZ,
    last_reviewed_by        UUID,
    last_reviewed_by_name   VARCHAR(255),
    created_by              UUID,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    deleted_at              TIMESTAMPTZ,
    deleted_by              UUID,
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_lc_obligations_tenant ON legalcompliance_regulatory_obligations (tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_lc_obligations_review_date ON legalcompliance_regulatory_obligations (tenant_id, review_date) WHERE deleted_at IS NULL;
CREATE INDEX idx_lc_obligations_status ON legalcompliance_regulatory_obligations (tenant_id, status) WHERE deleted_at IS NULL;

-- ── Litigation / dispute register ──────────────────────────────────────────

CREATE TABLE legalcompliance_litigation_matters (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    matter_number           VARCHAR(30) NOT NULL,
    title                   VARCHAR(255) NOT NULL,
    matter_type             VARCHAR(20) NOT NULL,
    status                  VARCHAR(20) NOT NULL,
    opposing_party          VARCHAR(255) NOT NULL,
    our_side                VARCHAR(20),
    estimated_exposure      NUMERIC(15,2),
    legal_representative    VARCHAR(255),
    court_or_forum          VARCHAR(255),
    case_reference          VARCHAR(100),
    opened_date             DATE NOT NULL,
    next_key_date           DATE,
    closed_date             DATE,
    description             TEXT,
    outcome_notes           TEXT,
    linked_contract_id      UUID,
    created_by              UUID,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    deleted_at              TIMESTAMPTZ,
    deleted_by              UUID,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_matter_number UNIQUE (tenant_id, matter_number)
);

CREATE INDEX idx_lc_matters_tenant ON legalcompliance_litigation_matters (tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_lc_matters_status ON legalcompliance_litigation_matters (tenant_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_lc_matters_next_key_date ON legalcompliance_litigation_matters (tenant_id, next_key_date) WHERE deleted_at IS NULL;

-- ── POPIA processing-activity register ─────────────────────────────────────

CREATE TABLE legalcompliance_popia_activities (
    id                              UUID PRIMARY KEY,
    tenant_id                       UUID NOT NULL,
    activity_name                   VARCHAR(255) NOT NULL,
    data_category                   VARCHAR(20) NOT NULL,
    purpose                         TEXT,
    lawful_basis                    VARCHAR(30) NOT NULL,
    responsible_department          VARCHAR(150),
    responsible_user_id             UUID,
    responsible_user_name           VARCHAR(255),
    retention_period_description    VARCHAR(500),
    cross_border_transfer           BOOLEAN NOT NULL DEFAULT FALSE,
    cross_border_details            VARCHAR(500),
    security_measures               TEXT,
    review_date                     DATE,
    active                          BOOLEAN NOT NULL DEFAULT TRUE,
    created_by                      UUID,
    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL,
    deleted_at                      TIMESTAMPTZ,
    deleted_by                      UUID,
    version                         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_lc_popia_tenant ON legalcompliance_popia_activities (tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_lc_popia_category ON legalcompliance_popia_activities (tenant_id, data_category) WHERE deleted_at IS NULL;

-- ── DSAR (data subject access request) tracking ────────────────────────────

CREATE TABLE legalcompliance_dsar_requests (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    request_number          VARCHAR(30) NOT NULL,
    request_type            VARCHAR(20) NOT NULL,
    data_category            VARCHAR(20) NOT NULL,
    requester_name          VARCHAR(255) NOT NULL,
    requester_email         VARCHAR(255),
    requester_contact       VARCHAR(100),
    received_date           DATE NOT NULL,
    due_date                DATE NOT NULL,
    status                  VARCHAR(20) NOT NULL,
    assigned_to_user_id     UUID,
    assigned_to_user_name   VARCHAR(255),
    resolution_notes        TEXT,
    completed_date          DATE,
    created_by              UUID,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    deleted_at              TIMESTAMPTZ,
    deleted_by              UUID,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_dsar_number UNIQUE (tenant_id, request_number)
);

CREATE INDEX idx_lc_dsar_tenant ON legalcompliance_dsar_requests (tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_lc_dsar_status ON legalcompliance_dsar_requests (tenant_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_lc_dsar_due_date ON legalcompliance_dsar_requests (tenant_id, due_date) WHERE deleted_at IS NULL;

-- ── Module catalogue + permission seed ─────────────────────────────────────

-- Column list matches AdminLookupService.createModule()'s own confirmed,
-- real INSERT exactly (key, name, description, monthly_price, icon,
-- category, sort_order, is_active) — deliberately not adding currency/
-- created_at/admin_notes, which that confirmed-working statement omits
-- too (the table evidently defaults or allows null for those).
INSERT INTO module_catalogue (key, name, description, monthly_price, icon, category, sort_order, is_active)
VALUES ('legalcompliance', 'Legal / Compliance',
        'Regulatory obligation tracker, litigation register, and POPIA processing-activity register for the business''s own legal exposure.',
        0, 'Scale', 'COMPLIANCE', 999, true)
ON CONFLICT (key) DO NOTHING;

INSERT INTO permissions (id, name, description)
VALUES
    (gen_random_uuid(), 'LEGALCOMPLIANCE_READ',   'View Legal / Compliance data'),
    (gen_random_uuid(), 'LEGALCOMPLIANCE_MANAGE', 'Create and manage Legal / Compliance records'),
    (gen_random_uuid(), 'LEGALCOMPLIANCE_ADMIN',  'Full administrative access to Legal / Compliance')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('LEGALCOMPLIANCE_READ', 'LEGALCOMPLIANCE_MANAGE', 'LEGALCOMPLIANCE_ADMIN')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- NOTE — monthly_price is left at 0 deliberately. This engagement has an
-- open, undecided pricing/packaging track (Track 5 in the handoff: discount
-- engine, named packs) that this module should slot into once that work
-- resumes, not a price guessed here ahead of that decision.
