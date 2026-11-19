-- ============================================================================
-- Legal / Compliance — Phase 4, part 2: privacy impact assessments, PAIA requests, regulator filings, FICA / RMCP, third-party compliance.
-- Depends on V289. Confirm the next free Flyway version before applying.
--
-- The existing POPIA processing register and DSAR tables (V255) are untouched; a PIA optionally points at a processing activity.
-- Nothing here files anything with a regulator: filings and FICA escalations record what the organisation did and when.
-- FICA records can only be created for a tenant whose FICA profile says it is an accountable institution (enforced in the service).
-- ============================================================================

-- ── Privacy impact assessments ─────────────────────────────────────────────
CREATE TABLE legalcompliance_pias (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    reference          VARCHAR(20) NOT NULL,
    title              VARCHAR(255) NOT NULL,
    popia_activity_id  UUID REFERENCES legalcompliance_popia_activities (id),
    description        TEXT NOT NULL,
    necessity          TEXT NOT NULL,
    risks              TEXT NOT NULL,
    mitigations        TEXT NOT NULL,
    residual_risk      VARCHAR(10) NOT NULL,
    status             VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
    assessor_id        UUID,
    assessor_name      VARCHAR(255) NOT NULL,
    submitted_at       TIMESTAMPTZ,
    reviewer_id        UUID,
    reviewer_name      VARCHAR(255),
    reviewed_at        TIMESTAMPTZ,
    review_note        TEXT,
    next_review_due    DATE,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_pia_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_lc_pia_risk CHECK (residual_risk IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT ck_lc_pia_status CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED'))
);
CREATE INDEX idx_lc_pia_tenant ON legalcompliance_pias (tenant_id, status);

-- ── PAIA requests ──────────────────────────────────────────────────────────
CREATE TABLE legalcompliance_paia_requests (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    reference            VARCHAR(20) NOT NULL,
    requester_name       VARCHAR(255) NOT NULL,
    requester_contact    VARCHAR(255),
    on_behalf_of         VARCHAR(255),
    record_description   TEXT NOT NULL,
    received_date        DATE NOT NULL,
    due_date             DATE NOT NULL,
    extended_until       DATE,
    extension_reason     TEXT,
    status               VARCHAR(10) NOT NULL DEFAULT 'RECEIVED',
    assigned_to_name     VARCHAR(255),
    fee_amount           NUMERIC(12,2),
    fee_paid_on          DATE,
    decision             VARCHAR(20),
    decision_date        DATE,
    decision_reason      TEXT,
    decided_by_name      VARCHAR(255),
    complaint_lodged_on  DATE,
    complaint_reference  VARCHAR(100),
    complaint_outcome    TEXT,
    closed_at            TIMESTAMPTZ,
    created_by           UUID,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_paia_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_lc_paia_status CHECK (status IN ('RECEIVED','DECIDED','CLOSED')),
    CONSTRAINT ck_lc_paia_decision CHECK (decision IS NULL OR decision IN ('GRANTED','PARTIALLY_GRANTED','REFUSED','TRANSFERRED')),
    CONSTRAINT ck_lc_paia_ext CHECK (extended_until IS NULL OR extended_until > due_date)
);
CREATE INDEX idx_lc_paia_tenant ON legalcompliance_paia_requests (tenant_id, status, due_date);

-- ── Regulator filings / registrations (records only) ───────────────────────
CREATE TABLE legalcompliance_filings (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    reference            VARCHAR(20) NOT NULL,
    filing_type          VARCHAR(40) NOT NULL,
    title                VARCHAR(255) NOT NULL,
    period_label         VARCHAR(50),
    due_date             DATE NOT NULL,
    owner_name           VARCHAR(255) NOT NULL,
    status               VARCHAR(15) NOT NULL DEFAULT 'PLANNED',
    prepared_at          TIMESTAMPTZ,
    submitted_on         DATE,
    submitted_by_name    VARCHAR(255),
    regulator_reference  VARCHAR(100),
    proof_snapshot       JSONB,
    notes                TEXT,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_filing_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_lc_filing_type CHECK (filing_type IN ('INFORMATION_OFFICER_REGISTRATION','PAIA_MANUAL_REVIEW','PAIA_ANNUAL_REPORT','POPIA_SELF_ASSESSMENT','OTHER')),
    CONSTRAINT ck_lc_filing_status CHECK (status IN ('PLANNED','PREPARED','SUBMITTED','ACKNOWLEDGED'))
);
CREATE INDEX idx_lc_filing_tenant ON legalcompliance_filings (tenant_id, status, due_date);

-- ── FICA / RMCP ────────────────────────────────────────────────────────────
CREATE TABLE legalcompliance_fica_profiles (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    accountable_institution BOOLEAN NOT NULL DEFAULT FALSE,
    basis                   TEXT NOT NULL,
    compliance_officer_name VARCHAR(255),
    rmcp_policy_id          UUID REFERENCES legalcompliance_policies (id),
    rmcp_approved_on        DATE,
    rmcp_next_review        DATE,
    updated_by_name         VARCHAR(255),
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_fica_profile UNIQUE (tenant_id)
);

CREATE TABLE legalcompliance_fica_parties (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    reference             VARCHAR(20) NOT NULL,
    party_name            VARCHAR(255) NOT NULL,
    party_type            VARCHAR(12) NOT NULL,
    identifier_hint       VARCHAR(20),
    risk_rating           VARCHAR(10) NOT NULL,
    pep                   BOOLEAN NOT NULL DEFAULT FALSE,
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    sanctions_screened_on DATE,
    verified_on           DATE,
    verified_by_id        UUID,
    verified_by_name      VARCHAR(255),
    senior_approval_name  VARCHAR(255),
    next_review_due       DATE,
    notes                 TEXT,
    created_by            UUID,
    created_by_name       VARCHAR(255),
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_fica_party_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_lc_fica_party_type CHECK (party_type IN ('INDIVIDUAL','COMPANY','TRUST','OTHER')),
    CONSTRAINT ck_lc_fica_party_risk CHECK (risk_rating IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT ck_lc_fica_party_status CHECK (status IN ('PENDING','VERIFIED','ENHANCED_REQUIRED','DECLINED'))
);
CREATE INDEX idx_lc_fica_party_tenant ON legalcompliance_fica_parties (tenant_id, status, next_review_due);

CREATE TABLE legalcompliance_fica_beneficial_owners (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    party_id          UUID NOT NULL REFERENCES legalcompliance_fica_parties (id) ON DELETE CASCADE,
    owner_name        VARCHAR(255) NOT NULL,
    ownership_percent NUMERIC(5,2),
    control_basis     VARCHAR(255),
    verified          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_lc_bo_pct CHECK (ownership_percent IS NULL OR (ownership_percent > 0 AND ownership_percent <= 100))
);
CREATE INDEX idx_lc_bo_party ON legalcompliance_fica_beneficial_owners (party_id);

CREATE TABLE legalcompliance_fica_escalations (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    reference          VARCHAR(20) NOT NULL,
    party_id           UUID REFERENCES legalcompliance_fica_parties (id),
    description        TEXT NOT NULL,
    raised_by_id       UUID,
    raised_by_name     VARCHAR(255) NOT NULL,
    status             VARCHAR(15) NOT NULL DEFAULT 'RAISED',
    decision           VARCHAR(15),
    decision_note      TEXT,
    decided_by_id      UUID,
    decided_by_name    VARCHAR(255),
    decided_at         TIMESTAMPTZ,
    external_reference VARCHAR(100),
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_fica_esc_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_lc_fica_esc_status CHECK (status IN ('RAISED','UNDER_REVIEW','DECIDED')),
    CONSTRAINT ck_lc_fica_esc_decision CHECK (decision IS NULL OR decision IN ('REPORTED','NOT_REPORTED'))
);
CREATE INDEX idx_lc_fica_esc_tenant ON legalcompliance_fica_escalations (tenant_id, status);

-- ── Third-party compliance ─────────────────────────────────────────────────
CREATE TABLE legalcompliance_third_parties (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    reference             VARCHAR(20) NOT NULL,
    name                  VARCHAR(255) NOT NULL,
    party_type            VARCHAR(20) NOT NULL,
    criticality           VARCHAR(10) NOT NULL,
    risk_rating           VARCHAR(10) NOT NULL,
    status                VARCHAR(12) NOT NULL DEFAULT 'ONBOARDING',
    owner_name            VARCHAR(255) NOT NULL,
    supplier_ref          UUID,
    handles_personal_info BOOLEAN NOT NULL DEFAULT FALSE,
    review_months         INTEGER NOT NULL DEFAULT 12,
    last_reviewed_on      DATE,
    next_review_due       DATE,
    approved_by_id        UUID,
    approved_by_name      VARCHAR(255),
    approved_on           DATE,
    notes                 TEXT,
    created_by            UUID,
    created_by_name       VARCHAR(255),
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_tp_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_lc_tp_type CHECK (party_type IN ('SUPPLIER','PARTNER','CONTRACTOR','SERVICE_PROVIDER')),
    CONSTRAINT ck_lc_tp_crit CHECK (criticality IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_lc_tp_risk CHECK (risk_rating IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT ck_lc_tp_status CHECK (status IN ('ONBOARDING','APPROVED','SUSPENDED','OFFBOARDED')),
    CONSTRAINT ck_lc_tp_months CHECK (review_months BETWEEN 1 AND 60)
);
CREATE INDEX idx_lc_tp_tenant ON legalcompliance_third_parties (tenant_id, status, next_review_due);

CREATE TABLE legalcompliance_third_party_items (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    third_party_id   UUID NOT NULL REFERENCES legalcompliance_third_parties (id) ON DELETE CASCADE,
    kind             VARCHAR(20) NOT NULL,
    title            VARCHAR(255) NOT NULL,
    issuer           VARCHAR(255),
    reference_no     VARCHAR(100),
    required         BOOLEAN NOT NULL DEFAULT TRUE,
    status           VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    valid_to         DATE,
    verified_by_name VARCHAR(255),
    verified_on      DATE,
    note             TEXT,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_lc_tpi_kind CHECK (kind IN ('DILIGENCE_CHECK','CERTIFICATE','OPERATOR_AGREEMENT','INSURANCE','OTHER')),
    CONSTRAINT ck_lc_tpi_status CHECK (status IN ('PENDING','VERIFIED','REJECTED'))
);
CREATE INDEX idx_lc_tpi_tp ON legalcompliance_third_party_items (third_party_id);
CREATE INDEX idx_lc_tpi_expiry ON legalcompliance_third_party_items (valid_to) WHERE status = 'VERIFIED' AND valid_to IS NOT NULL;

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'LEGALCOMPLIANCE_PRIVACY_MANAGE',      'Manage privacy impact assessments, PAIA requests and regulator filings'),
    (gen_random_uuid(), 'LEGALCOMPLIANCE_PRIVACY_APPROVE',     'Approve or reject privacy impact assessments; cannot approve your own'),
    (gen_random_uuid(), 'LEGALCOMPLIANCE_FICA_MANAGE',         'Manage the FICA profile, customer due diligence and unusual-activity escalations (restricted)'),
    (gen_random_uuid(), 'LEGALCOMPLIANCE_THIRDPARTY_MANAGE',   'Manage third-party profiles, due-diligence items and reviews'),
    (gen_random_uuid(), 'LEGALCOMPLIANCE_THIRDPARTY_APPROVE',  'Approve third parties; cannot approve one you created')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('LEGALCOMPLIANCE_PRIVACY_MANAGE','LEGALCOMPLIANCE_PRIVACY_APPROVE','LEGALCOMPLIANCE_FICA_MANAGE','LEGALCOMPLIANCE_THIRDPARTY_MANAGE','LEGALCOMPLIANCE_THIRDPARTY_APPROVE')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
