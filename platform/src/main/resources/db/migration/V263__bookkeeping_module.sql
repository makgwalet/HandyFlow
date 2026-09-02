-- ============================================================================
-- Module 6: Bookkeeping Services (outsourced bookkeeping practice)
-- VERSION NUMBER NOT CONFIRMED — this assumes V263 follows Module 5b's own
-- V262 sequentially. READ THE REAL FLYWAY MIGRATION HISTORY BEFORE APPLYING.
-- ============================================================================

CREATE TABLE bk_profiles (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    practice_name         VARCHAR(255) NOT NULL,
    registration_number   VARCHAR(100),
    contact_email         VARCHAR(255),
    contact_phone         VARCHAR(50),
    notes                 TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version               BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_bk_profiles_tenant ON bk_profiles(tenant_id);

CREATE TABLE bk_clients (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    client_code           VARCHAR(50) NOT NULL,
    trading_name          VARCHAR(255) NOT NULL,
    registration_number   VARCHAR(100),
    vat_number            VARCHAR(50),
    contact_name          VARCHAR(255),
    contact_email         VARCHAR(255),
    contact_phone         VARCHAR(50),
    address               TEXT,
    status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at            TIMESTAMPTZ,
    version               BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_bk_clients_tenant ON bk_clients(tenant_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_bk_clients_tenant_code ON bk_clients(tenant_id, client_code) WHERE deleted_at IS NULL;

CREATE TABLE bk_accounts (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    client_id         UUID NOT NULL REFERENCES bk_clients(id),
    account_code      VARCHAR(20) NOT NULL,
    account_name      VARCHAR(255) NOT NULL,
    account_type      VARCHAR(20) NOT NULL,
    account_subtype   VARCHAR(50),
    system            BOOLEAN NOT NULL DEFAULT FALSE,
    opening_balance   NUMERIC(15,2) NOT NULL DEFAULT 0,
    description       TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version           BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_bk_accounts_tenant ON bk_accounts(tenant_id);
CREATE INDEX idx_bk_accounts_client ON bk_accounts(client_id);
CREATE UNIQUE INDEX uq_bk_accounts_client_code ON bk_accounts(client_id, account_code);

CREATE TABLE bk_periods (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    client_id      UUID NOT NULL REFERENCES bk_clients(id),
    period_year    INTEGER NOT NULL,
    period_month   INTEGER NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    closed_at      TIMESTAMPTZ,
    closed_by      UUID,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_bk_period_month_range CHECK (period_month BETWEEN 1 AND 12)
);
CREATE INDEX idx_bk_periods_tenant ON bk_periods(tenant_id);
CREATE UNIQUE INDEX uq_bk_periods_client_year_month ON bk_periods(client_id, period_year, period_month);

CREATE TABLE bk_journal_entries (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    client_id      UUID NOT NULL REFERENCES bk_clients(id),
    period_id      UUID NOT NULL REFERENCES bk_periods(id),
    entry_number   VARCHAR(50) NOT NULL,
    entry_date     DATE NOT NULL,
    description    TEXT,
    reference      VARCHAR(255),
    entry_type     VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    status         VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by     UUID,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    posted_at      TIMESTAMPTZ,
    deleted_at     TIMESTAMPTZ,
    version        BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_bk_journal_entries_tenant ON bk_journal_entries(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_bk_journal_entries_client ON bk_journal_entries(client_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_bk_journal_entries_period ON bk_journal_entries(period_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_bk_journal_entries_number ON bk_journal_entries(entry_number);

CREATE TABLE bk_journal_lines (
    id                UUID PRIMARY KEY,
    journal_entry_id  UUID NOT NULL REFERENCES bk_journal_entries(id),
    account_id        UUID NOT NULL REFERENCES bk_accounts(id),
    description       TEXT,
    debit_amount      NUMERIC(15,2) NOT NULL DEFAULT 0,
    credit_amount     NUMERIC(15,2) NOT NULL DEFAULT 0,
    sort_order        INTEGER NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_bk_journal_lines_entry ON bk_journal_lines(journal_entry_id);
CREATE INDEX idx_bk_journal_lines_account ON bk_journal_lines(account_id);

CREATE TABLE bk_bank_accounts (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    client_id         UUID NOT NULL REFERENCES bk_clients(id),
    account_id        UUID REFERENCES bk_accounts(id),
    bank_name         VARCHAR(255) NOT NULL,
    account_name      VARCHAR(255) NOT NULL,
    account_number    VARCHAR(100) NOT NULL,
    branch_code       VARCHAR(20),
    account_type      VARCHAR(20) NOT NULL DEFAULT 'CURRENT',
    currency          VARCHAR(3) NOT NULL DEFAULT 'ZAR',
    current_balance   NUMERIC(15,2) NOT NULL DEFAULT 0,
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ,
    version           BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_bk_bank_accounts_tenant ON bk_bank_accounts(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_bk_bank_accounts_client ON bk_bank_accounts(client_id) WHERE deleted_at IS NULL;

CREATE TABLE bk_bank_transactions (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    client_id          UUID NOT NULL REFERENCES bk_clients(id),
    bank_account_id    UUID NOT NULL REFERENCES bk_bank_accounts(id),
    transaction_date   DATE NOT NULL,
    description        TEXT,
    reference          VARCHAR(255),
    amount             NUMERIC(15,2) NOT NULL,
    transaction_type   VARCHAR(10) NOT NULL,
    balance_after      NUMERIC(15,2),
    reconciled         BOOLEAN NOT NULL DEFAULT FALSE,
    reconciled_at      TIMESTAMPTZ,
    journal_line_id    UUID,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_bk_bank_tx_tenant ON bk_bank_transactions(tenant_id);
CREATE INDEX idx_bk_bank_tx_client ON bk_bank_transactions(client_id);
CREATE INDEX idx_bk_bank_tx_account ON bk_bank_transactions(bank_account_id);
CREATE INDEX idx_bk_bank_tx_unreconciled_sweep ON bk_bank_transactions(transaction_date) WHERE reconciled = FALSE;

CREATE TABLE bk_time_entries (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    client_id          UUID NOT NULL REFERENCES bk_clients(id),
    practitioner_id    UUID,
    practitioner_name  VARCHAR(255),
    entry_date         DATE NOT NULL,
    activity_type      VARCHAR(50) NOT NULL,
    description        TEXT,
    hours              NUMERIC(5,2) NOT NULL,
    hourly_rate        NUMERIC(10,2) NOT NULL,
    billable           BOOLEAN NOT NULL DEFAULT TRUE,
    status             VARCHAR(20) NOT NULL DEFAULT 'UNBILLED',
    invoice_id         UUID,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_bk_time_entries_tenant ON bk_time_entries(tenant_id);
CREATE INDEX idx_bk_time_entries_client ON bk_time_entries(client_id);
CREATE INDEX idx_bk_time_entries_unbilled ON bk_time_entries(client_id, entry_date) WHERE status = 'UNBILLED';

CREATE TABLE bk_service_agreements (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL REFERENCES bk_clients(id),
    billing_type    VARCHAR(30) NOT NULL,
    monthly_fee     NUMERIC(15,2),
    hourly_rate     NUMERIC(15,2),
    start_date      DATE NOT NULL,
    end_date        DATE,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_bk_agreement_end_after_start CHECK (end_date IS NULL OR end_date > start_date)
);
CREATE INDEX idx_bk_agreements_tenant ON bk_service_agreements(tenant_id);
CREATE INDEX idx_bk_agreements_client ON bk_service_agreements(client_id);
CREATE INDEX idx_bk_agreements_expiry_sweep ON bk_service_agreements(end_date) WHERE status = 'ACTIVE' AND end_date IS NOT NULL;

CREATE TABLE bk_invoices (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL REFERENCES bk_clients(id),
    invoice_number  VARCHAR(50) NOT NULL,
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,
    issue_date      DATE NOT NULL,
    due_date        DATE NOT NULL,
    subtotal        NUMERIC(15,2) NOT NULL,
    vat_amount      NUMERIC(15,2) NOT NULL DEFAULT 0,
    total           NUMERIC(15,2) NOT NULL,
    amount_paid     NUMERIC(15,2) NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    sent_at         TIMESTAMPTZ,
    paid_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_bk_invoice_period_order CHECK (period_end >= period_start)
);
CREATE INDEX idx_bk_invoices_tenant ON bk_invoices(tenant_id);
CREATE INDEX idx_bk_invoices_client ON bk_invoices(client_id);
CREATE INDEX idx_bk_invoices_overdue_sweep ON bk_invoices(due_date) WHERE status IN ('SENT','PARTIAL');
CREATE UNIQUE INDEX uq_bk_invoices_number ON bk_invoices(invoice_number);

CREATE TABLE bk_portal_access_grants (
    id                        UUID PRIMARY KEY,
    tenant_id                 UUID NOT NULL,
    client_id                 UUID NOT NULL REFERENCES bk_clients(id),
    portal_user_id            UUID,
    invite_email              VARCHAR(255) NOT NULL,
    status                    VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    invite_token              VARCHAR(255),
    invite_token_expires_at   TIMESTAMPTZ,
    invited_by                UUID,
    invited_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    accepted_at               TIMESTAMPTZ,
    revoked_by                UUID,
    revoked_at                TIMESTAMPTZ
);
CREATE INDEX idx_bk_portal_grants_tenant ON bk_portal_access_grants(tenant_id);
CREATE INDEX idx_bk_portal_grants_client ON bk_portal_access_grants(client_id);
CREATE INDEX idx_bk_portal_grants_portal_user ON bk_portal_access_grants(portal_user_id);
CREATE UNIQUE INDEX uq_bk_portal_grants_invite_token ON bk_portal_access_grants(invite_token) WHERE invite_token IS NOT NULL;

-- ── Module catalogue + permissions ──────────────────────────────────────────

INSERT INTO module_catalogue (key, name, description, category, monthly_price, is_active)
VALUES ('bookkeeping', 'Bookkeeping Services', 'Per-client bank-feed import, transaction categorization and reconciliation against a client-scoped chart of accounts and journal, monthly period close, staff time-logging, retainer-or-time-and-materials service agreements, GL-posted invoicing and a client portal for a bookkeeping practice serving a portfolio of external clients.', 'Finance', 329.00, TRUE)
ON CONFLICT (key) DO NOTHING;

INSERT INTO permissions (id, name, description)
VALUES
    (gen_random_uuid(), 'BOOKKEEPING_READ',   'View clients, chart of accounts, periods, journals, bank transactions, time entries, agreements and invoices'),
    (gen_random_uuid(), 'BOOKKEEPING_MANAGE', 'Manage clients, chart of accounts, periods, journal entries, bank imports/reconciliation, time entries and service agreements'),
    (gen_random_uuid(), 'BOOKKEEPING_ADMIN',  'Delete bookkeeping records, generate invoices, record payments and revoke portal access')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
AND p.name IN ('BOOKKEEPING_READ', 'BOOKKEEPING_MANAGE', 'BOOKKEEPING_ADMIN')
AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);
