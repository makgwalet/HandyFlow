-- ============================================================================
-- Collections Agency (outsourced-provider) module — baseline schema + seed.
--
-- *** VERSION NUMBER NOT CONFIRMED — READ BEFORE APPLYING ***
-- V257 follows directly from V256 (debtcollection, this same engagement,
-- itself unconfirmed). Renumber together with V255/V256 if the real next
-- free version differs.
--
-- *** MODULE CATALOGUE / PERMISSION SEED SHAPE — SAME CAVEAT AS V255/V256 ***
-- Module key "collectionsagency" (lowercase, no separator), permissions
-- COLLECTIONSAGENCY_READ/_MANAGE/_ADMIN — same confirmed naming convention.
-- ============================================================================

-- ── Agency profile (one per tenant) ─────────────────────────────────────────

CREATE TABLE collagency_profile (
    id                              UUID PRIMARY KEY,
    tenant_id                       UUID NOT NULL UNIQUE,
    agency_name                     VARCHAR(255) NOT NULL,
    firm_registration_number        VARCHAR(100),
    firm_registration_expiry_date   DATE,
    default_commission_pct          NUMERIC(5,2),
    contact_email                   VARCHAR(255),
    contact_phone                   VARCHAR(50),
    physical_address                TEXT,
    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL
);

-- ── Creditor client portfolio ───────────────────────────────────────────────

CREATE TABLE collagency_clients (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    trading_name            VARCHAR(255) NOT NULL,
    registration_number     VARCHAR(100),
    commission_rate_pct     NUMERIC(5,2),
    contact_name            VARCHAR(255),
    contact_email           VARCHAR(255),
    contact_phone           VARCHAR(50),
    address                 TEXT,
    trust_balance           NUMERIC(15,2) NOT NULL DEFAULT 0,
    onboarded_at            DATE,
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes                   TEXT,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_collagency_clients_tenant ON collagency_clients (tenant_id) WHERE deleted_at IS NULL;

-- ── Individual collector registrations ──────────────────────────────────────

CREATE TABLE collagency_collectors (
    id                              UUID PRIMARY KEY,
    tenant_id                       UUID NOT NULL,
    user_id                         UUID,
    full_name                       VARCHAR(255) NOT NULL,
    registration_number             VARCHAR(100),
    registration_expiry_date        DATE,
    email                           VARCHAR(255),
    phone                           VARCHAR(50),
    active                          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL,
    deleted_at                      TIMESTAMPTZ
);

CREATE INDEX idx_collagency_collectors_tenant ON collagency_collectors (tenant_id) WHERE deleted_at IS NULL;

-- ── Placement batches (handover events) ─────────────────────────────────────

CREATE TABLE collagency_placement_batches (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    client_id               UUID NOT NULL REFERENCES collagency_clients (id),
    batch_reference         VARCHAR(100),
    placed_date             DATE NOT NULL,
    total_accounts          INTEGER NOT NULL,
    total_placed_value      NUMERIC(15,2) NOT NULL,
    acknowledged_at         TIMESTAMPTZ,
    acknowledged_by         UUID,
    notes                   TEXT,
    created_at              TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_collagency_batches_client ON collagency_placement_batches (tenant_id, client_id);

-- ── Debtor accounts (the portfolio itself) ──────────────────────────────────

CREATE TABLE collagency_debtor_accounts (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    client_id                   UUID NOT NULL REFERENCES collagency_clients (id),
    placement_batch_id          UUID REFERENCES collagency_placement_batches (id),
    account_reference           VARCHAR(100),
    debtor_name                 VARCHAR(255) NOT NULL,
    debtor_id_number             VARCHAR(30),
    debtor_email                VARCHAR(255),
    debtor_phone                VARCHAR(50),
    debtor_address              TEXT,
    original_creditor_name      VARCHAR(255) NOT NULL,
    original_debt_date          DATE,
    original_debt_amount        NUMERIC(15,2) NOT NULL,
    current_balance             NUMERIC(15,2) NOT NULL,
    status                      VARCHAR(25) NOT NULL DEFAULT 'PLACED',
    assigned_collector_id       UUID REFERENCES collagency_collectors (id),
    placed_date                 DATE NOT NULL,
    closed_date                 DATE,
    notes                       TEXT,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    deleted_at                  TIMESTAMPTZ,
    version                     BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_collagency_debtors_tenant ON collagency_debtor_accounts (tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_collagency_debtors_client ON collagency_debtor_accounts (tenant_id, client_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_collagency_debtors_status ON collagency_debtor_accounts (tenant_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_collagency_debtors_batch ON collagency_debtor_accounts (tenant_id, placement_batch_id) WHERE deleted_at IS NULL;

-- ── Contact log (NCA-compliance trail, append-only) ────────────────────────

CREATE TABLE collagency_contact_logs (
    id                                  UUID PRIMARY KEY,
    tenant_id                           UUID NOT NULL,
    debtor_account_id                   UUID NOT NULL REFERENCES collagency_debtor_accounts (id),
    contact_date                        DATE NOT NULL,
    contact_method                      VARCHAR(20) NOT NULL,
    outcome                             VARCHAR(25) NOT NULL,
    disclosed_third_party_collector     BOOLEAN NOT NULL,
    disclosed_original_creditor         BOOLEAN NOT NULL,
    disclosed_debtor_rights             BOOLEAN NOT NULL,
    notes                               TEXT,
    promised_payment_date               DATE,
    promised_payment_amount             NUMERIC(15,2),
    recorded_by_user_id                 UUID NOT NULL,
    recorded_by_user_name               VARCHAR(255),
    created_at                          TIMESTAMPTZ NOT NULL,
    -- Enforced at the application layer (CollAgencyContactLog.record()) as
    -- well as here — belt-and-braces for a compliance-critical constraint,
    -- since this table could in principle also be written to by a future
    -- data-migration or admin tool that bypasses the entity's factory method.
    CONSTRAINT chk_collagency_contact_disclosures CHECK (
        disclosed_third_party_collector = TRUE
        AND disclosed_original_creditor = TRUE
        AND disclosed_debtor_rights = TRUE
    )
);

CREATE INDEX idx_collagency_contact_logs_debtor ON collagency_contact_logs (tenant_id, debtor_account_id);

-- ── Payment plans ───────────────────────────────────────────────────────────

CREATE TABLE collagency_payment_plans (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    debtor_account_id       UUID NOT NULL REFERENCES collagency_debtor_accounts (id),
    status                  VARCHAR(15) NOT NULL DEFAULT 'ACTIVE',
    total_agreed_amount     NUMERIC(15,2) NOT NULL,
    installment_amount      NUMERIC(15,2) NOT NULL,
    frequency               VARCHAR(15) NOT NULL,
    start_date              DATE NOT NULL,
    next_due_date           DATE,
    number_of_installments  INTEGER NOT NULL,
    installments_paid       INTEGER NOT NULL DEFAULT 0,
    notes                   TEXT,
    created_at              TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_collagency_plans_debtor ON collagency_payment_plans (tenant_id, debtor_account_id);
CREATE INDEX idx_collagency_plans_due ON collagency_payment_plans (next_due_date) WHERE status = 'ACTIVE';

-- ── Trust ledger (self-contained — never posts to the real GL) ────────────

CREATE TABLE collagency_trust_transactions (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    client_id               UUID NOT NULL REFERENCES collagency_clients (id),
    debtor_account_id       UUID REFERENCES collagency_debtor_accounts (id),
    transaction_type        VARCHAR(15) NOT NULL,
    amount                  NUMERIC(15,2) NOT NULL,
    transaction_date        DATE NOT NULL,
    reference                VARCHAR(100),
    notes                   TEXT,
    recorded_by_user_id     UUID,
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_collagency_trust_type CHECK (transaction_type IN ('RECEIPT', 'REMITTANCE')),
    CONSTRAINT chk_collagency_trust_receipt_has_debtor CHECK (
        (transaction_type = 'RECEIPT' AND debtor_account_id IS NOT NULL)
        OR (transaction_type = 'REMITTANCE' AND debtor_account_id IS NULL)
    )
);

CREATE INDEX idx_collagency_trust_client ON collagency_trust_transactions (tenant_id, client_id);
CREATE INDEX idx_collagency_trust_debtor ON collagency_trust_transactions (tenant_id, debtor_account_id);

-- ── Commission invoices (the only thing that posts to the real GL) ────────

CREATE TABLE collagency_commission_invoices (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    client_id               UUID NOT NULL REFERENCES collagency_clients (id),
    invoice_number          VARCHAR(30) NOT NULL,
    description              TEXT NOT NULL,
    invoice_date            DATE NOT NULL,
    due_date                DATE NOT NULL,
    subtotal                NUMERIC(15,2) NOT NULL,
    vat_amount              NUMERIC(15,2) NOT NULL,
    total                   NUMERIC(15,2) NOT NULL,
    amount_paid             NUMERIC(15,2) NOT NULL DEFAULT 0,
    status                  VARCHAR(15) NOT NULL DEFAULT 'DRAFT',
    sent_at                 TIMESTAMPTZ,
    paid_at                 TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_collagency_invoice_number UNIQUE (tenant_id, invoice_number)
);

CREATE INDEX idx_collagency_invoices_client ON collagency_commission_invoices (tenant_id, client_id);

-- ── Client portal access grants (own table — not shared) ──────────────────

CREATE TABLE collagency_portal_access_grants (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    client_id                   UUID NOT NULL REFERENCES collagency_clients (id),
    portal_user_id               UUID,
    invite_email                VARCHAR(255) NOT NULL,
    status                       VARCHAR(15) NOT NULL DEFAULT 'PENDING',
    invite_token                 VARCHAR(100) UNIQUE,
    invite_token_expires_at      TIMESTAMPTZ,
    invited_by                   UUID,
    invited_at                   TIMESTAMPTZ NOT NULL,
    accepted_at                  TIMESTAMPTZ,
    revoked_by                   UUID,
    revoked_at                   TIMESTAMPTZ
);

CREATE INDEX idx_collagency_portal_grants_client ON collagency_portal_access_grants (tenant_id, client_id);
CREATE INDEX idx_collagency_portal_grants_user ON collagency_portal_access_grants (portal_user_id) WHERE status = 'ACTIVE';

-- ── Module catalogue + permission seed ─────────────────────────────────────

INSERT INTO module_catalogue (key, name, description, monthly_price, icon, category, sort_order, is_active)
VALUES ('collectionsagency', 'Collections Agency',
        'Multi-client debt collection agency practice management — debtor portfolios, trust accounting, commission billing, and Debt Collectors Act / NCA compliance tracking.',
        0, 'Banknote', 'FINANCE', 999, true)
ON CONFLICT (key) DO NOTHING;

INSERT INTO permissions (id, name, description)
VALUES
    (gen_random_uuid(), 'COLLECTIONSAGENCY_READ',   'View Collections Agency data'),
    (gen_random_uuid(), 'COLLECTIONSAGENCY_MANAGE', 'Create and manage Collections Agency records'),
    (gen_random_uuid(), 'COLLECTIONSAGENCY_ADMIN',  'Full administrative access to Collections Agency, including trust remittances and write-offs')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('COLLECTIONSAGENCY_READ', 'COLLECTIONSAGENCY_MANAGE', 'COLLECTIONSAGENCY_ADMIN')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- NOTE — monthly_price left at 0, same open-pricing-track caveat as V255/V256.
