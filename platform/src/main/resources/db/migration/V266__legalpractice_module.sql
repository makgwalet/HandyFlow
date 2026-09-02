-- ============================================================================
-- V266__legalpractice_module.sql
--
-- Legal Practice (legalpractice) module — 11 lp_-prefixed tables, matching
-- every domain/model entity field/type/nullability exactly (cross-checked
-- against the actual entity source, not guessed), plus module_catalogue +
-- LEGALPRACTICE_READ/_MANAGE/_ADMIN permission seed rows, mirroring
-- AdminLookupService.createModule()'s own INSERT shape (module_catalogue,
-- permissions, role_permissions granted to every ADMIN role) — the same
-- shape V264__agriculture_module.sql used.
--
-- ⚠️ WARNING — NOT INDEPENDENTLY CONFIRMED:
--   1. Version number V266 assumes it follows Agriculture's V264/V265
--      sequentially. This session's synced source excludes
--      src/main/resources, so the real Flyway history could not be
--      directly re-read. Confirm against the live flyway_schema_history
--      table before applying.
--   2. No hard FK constraints anywhere in this migration (client_id,
--      attorney_id, matter_id, invoice_id, etc. are all plain UUID
--      columns) — matching this engagement's own standing in-module
--      convention carried forward from every prior module (Agriculture,
--      Collections Agency, ...), itself flagged as unconfirmed against a
--      real migration in each of those modules' own status docs.
-- ============================================================================

-- ── lp_profiles ──────────────────────────────────────────────────────────
CREATE TABLE lp_profiles (
    id                       UUID PRIMARY KEY,
    tenant_id                UUID NOT NULL,
    firm_name                VARCHAR(255) NOT NULL,
    practice_number          VARCHAR(100),
    vat_number               VARCHAR(50),
    contact_email            VARCHAR(255),
    contact_phone            VARCHAR(50),
    trust_bank_name          VARCHAR(255),
    trust_account_number     VARCHAR(100),
    business_bank_name       VARCHAR(255),
    business_account_number  VARCHAR(100),
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    version                  BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_lp_profiles_tenant ON lp_profiles (tenant_id);

-- ── lp_clients ───────────────────────────────────────────────────────────
CREATE TABLE lp_clients (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    name                        VARCHAR(255) NOT NULL,
    email                       VARCHAR(255),
    phone                       VARCHAR(50),
    client_type                 VARCHAR(20) NOT NULL,
    id_or_registration_number   VARCHAR(100),
    trust_balance                NUMERIC(15,2) NOT NULL DEFAULT 0,
    status                      VARCHAR(20) NOT NULL,
    notes                       TEXT,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_lp_clients_tenant ON lp_clients (tenant_id);
CREATE INDEX idx_lp_clients_tenant_status ON lp_clients (tenant_id, status);

-- ── lp_attorneys ─────────────────────────────────────────────────────────
CREATE TABLE lp_attorneys (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    name              VARCHAR(255) NOT NULL,
    email             VARCHAR(255),
    phone             VARCHAR(50),
    role              VARCHAR(30) NOT NULL,
    admission_number  VARCHAR(100),
    hourly_rate       NUMERIC(12,2),
    employee_id       UUID,
    active            BOOLEAN NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    version           BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_lp_attorneys_tenant ON lp_attorneys (tenant_id);

-- ── lp_matters ───────────────────────────────────────────────────────────
CREATE TABLE lp_matters (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    client_id          UUID NOT NULL,
    attorney_id        UUID NOT NULL,
    matter_number      VARCHAR(100) NOT NULL,
    matter_type        VARCHAR(30) NOT NULL,
    matter_name        VARCHAR(255) NOT NULL,
    description        TEXT,
    billing_type       VARCHAR(20) NOT NULL,
    fixed_fee_amount   NUMERIC(15,2),
    status             VARCHAR(20) NOT NULL,
    opened_date        DATE NOT NULL,
    closed_date        DATE,
    notes              TEXT,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    version            BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_lp_matters_tenant ON lp_matters (tenant_id);
CREATE INDEX idx_lp_matters_tenant_client ON lp_matters (tenant_id, client_id);
CREATE INDEX idx_lp_matters_tenant_status ON lp_matters (tenant_id, status);

-- ── lp_retainer_agreements ───────────────────────────────────────────────
CREATE TABLE lp_retainer_agreements (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    client_id    UUID NOT NULL,
    monthly_fee  NUMERIC(15,2) NOT NULL,
    start_date   DATE NOT NULL,
    end_date     DATE,
    status       VARCHAR(20) NOT NULL,
    notes        TEXT,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    version      BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_lp_retainer_agreements_tenant_client ON lp_retainer_agreements (tenant_id, client_id);

-- ── lp_time_entries ──────────────────────────────────────────────────────
CREATE TABLE lp_time_entries (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    matter_id    UUID NOT NULL,
    attorney_id  UUID NOT NULL,
    entry_date   DATE NOT NULL,
    hours        NUMERIC(8,2) NOT NULL,
    hourly_rate  NUMERIC(12,2) NOT NULL,
    description  TEXT NOT NULL,
    billable     BOOLEAN NOT NULL DEFAULT true,
    status       VARCHAR(20) NOT NULL,
    invoice_id   UUID,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    version      BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_lp_time_entries_tenant_matter ON lp_time_entries (tenant_id, matter_id);
CREATE INDEX idx_lp_time_entries_tenant_matter_status ON lp_time_entries (tenant_id, matter_id, status);

-- ── lp_disbursements ─────────────────────────────────────────────────────
CREATE TABLE lp_disbursements (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    matter_id            UUID NOT NULL,
    disbursement_date    DATE NOT NULL,
    description          TEXT NOT NULL,
    amount               NUMERIC(15,2) NOT NULL,
    paid_from_trust      BOOLEAN NOT NULL DEFAULT false,
    status               VARCHAR(20) NOT NULL,
    invoice_id           UUID,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    version              BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_lp_disbursements_tenant_matter ON lp_disbursements (tenant_id, matter_id);
CREATE INDEX idx_lp_disbursements_tenant_matter_status ON lp_disbursements (tenant_id, matter_id, status);

-- ── lp_invoices ──────────────────────────────────────────────────────────
CREATE TABLE lp_invoices (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    client_id        UUID NOT NULL,
    matter_id        UUID,
    invoice_number   VARCHAR(100) NOT NULL,
    description      TEXT,
    issue_date       DATE NOT NULL,
    due_date         DATE,
    subtotal         NUMERIC(15,2) NOT NULL,
    vat_amount       NUMERIC(15,2) NOT NULL,
    total_amount     NUMERIC(15,2) NOT NULL,
    amount_paid      NUMERIC(15,2) NOT NULL DEFAULT 0,
    status           VARCHAR(20) NOT NULL,
    notes            TEXT,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    version          BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_lp_invoices_tenant_client ON lp_invoices (tenant_id, client_id);
CREATE INDEX idx_lp_invoices_tenant_status ON lp_invoices (tenant_id, status);

-- ── lp_trust_transactions ────────────────────────────────────────────────
-- Append-only (no version, no updated_at — matches the entity exactly:
-- LpTrustTransaction has neither). Two CHECK constraints, belt-and-braces
-- with LpTrustTransaction.validateTypeFieldCombination(), mirroring
-- V257__collectionsagency_module.sql's own two CHECK constraints.
CREATE TABLE lp_trust_transactions (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    client_id          UUID NOT NULL,
    matter_id          UUID,
    transaction_type   VARCHAR(30) NOT NULL,
    amount             NUMERIC(15,2) NOT NULL,
    transaction_date   DATE NOT NULL,
    invoice_id         UUID,
    payee              VARCHAR(255),
    reference          VARCHAR(255),
    captured_by        UUID,
    captured_by_name   VARCHAR(255),
    notes              TEXT,
    created_at         TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_lp_trust_transactions_amount_positive
        CHECK (amount > 0),

    CONSTRAINT ck_lp_trust_transactions_type_fields CHECK (
        (transaction_type = 'RECEIPT'
            AND invoice_id IS NULL AND payee IS NULL)
        OR (transaction_type = 'TRANSFER_TO_BUSINESS'
            AND invoice_id IS NOT NULL AND payee IS NULL)
        OR (transaction_type = 'DISBURSEMENT_PAYMENT'
            AND payee IS NOT NULL AND invoice_id IS NULL)
        OR (transaction_type = 'REFUND'
            AND payee IS NOT NULL AND invoice_id IS NULL)
    )
);
CREATE INDEX idx_lp_trust_transactions_tenant_client ON lp_trust_transactions (tenant_id, client_id);
CREATE INDEX idx_lp_trust_transactions_invoice ON lp_trust_transactions (invoice_id) WHERE invoice_id IS NOT NULL;

-- ── lp_matter_key_dates ──────────────────────────────────────────────────
CREATE TABLE lp_matter_key_dates (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    matter_id     UUID NOT NULL,
    date_type     VARCHAR(30) NOT NULL,
    due_date      DATE NOT NULL,
    description   TEXT NOT NULL,
    acknowledged  BOOLEAN NOT NULL DEFAULT false,
    status        VARCHAR(20) NOT NULL,
    notes         TEXT,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    version       BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_lp_matter_key_dates_tenant_matter ON lp_matter_key_dates (tenant_id, matter_id);
-- Non-tenant-prefixed index backing LpNotificationScheduler's cross-tenant
-- sweep (findDueUnacknowledgedAcrossTenants), mirroring V265's own index
-- for the scouting-followup sweep.
CREATE INDEX idx_lp_matter_key_dates_due_sweep
    ON lp_matter_key_dates (due_date)
    WHERE acknowledged = false AND status = 'PENDING';

-- ── lp_portal_access_grants ──────────────────────────────────────────────
-- Invite-token/email/status shape — matches the corrected LpPortalAccessGrant
-- entity exactly, mirroring acc_portal_access_grants/auditor_access_grants'
-- own confirmed-real column set (both read directly from real source this
-- session). portal_user_id is nullable and starts null: a grant is created
-- PENDING, invited by email, before a PortalUser necessarily exists yet; it
-- is linked once the invite is accepted.
CREATE TABLE lp_portal_access_grants (
    id                       UUID PRIMARY KEY,
    tenant_id                UUID NOT NULL,
    client_id                UUID NOT NULL,
    portal_user_id           UUID,
    invite_email             VARCHAR(255) NOT NULL,
    status                   VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    invite_token             VARCHAR(255),
    invite_token_expires_at  TIMESTAMPTZ,
    invited_by               UUID,
    invited_at               TIMESTAMPTZ NOT NULL,
    accepted_at              TIMESTAMPTZ,
    revoked_by               UUID,
    revoked_at               TIMESTAMPTZ,
    CONSTRAINT uq_lp_portal_access_grants_invite_token UNIQUE (invite_token)
);
CREATE INDEX idx_lp_portal_access_grants_tenant_client ON lp_portal_access_grants (tenant_id, client_id);
CREATE INDEX idx_lp_portal_access_grants_portal_user ON lp_portal_access_grants (portal_user_id, client_id);
CREATE INDEX idx_lp_portal_access_grants_invite_email ON lp_portal_access_grants (invite_email);

-- ============================================================================
-- Module catalogue + permission seed — mirrors AdminLookupService.createModule()'s
-- own INSERT shape exactly (module_catalogue, permissions with the standard
-- READ/MANAGE/ADMIN trio, role_permissions granted to every existing ADMIN
-- role), the same shape V264__agriculture_module.sql used.
-- ============================================================================

INSERT INTO module_catalogue (key, name, description, monthly_price, icon, category, sort_order, is_active)
VALUES ('legalpractice', 'Legal Practice', 'Client portfolio, matter management, trust accounting, and billing for law firms',
        399.00, 'Scale', 'Professional Services', 700, true)
ON CONFLICT (key) DO NOTHING;

INSERT INTO permissions (id, name, description)
VALUES
    (gen_random_uuid(), 'LEGALPRACTICE_READ', 'View Legal Practice data'),
    (gen_random_uuid(), 'LEGALPRACTICE_MANAGE', 'Create and manage Legal Practice records'),
    (gen_random_uuid(), 'LEGALPRACTICE_ADMIN', 'Full administrative access to Legal Practice')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('LEGALPRACTICE_READ', 'LEGALPRACTICE_MANAGE', 'LEGALPRACTICE_ADMIN')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
