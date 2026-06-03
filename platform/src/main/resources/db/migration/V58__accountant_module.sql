-- V58__accountant_module.sql
-- Accountant practice management module
--
-- FLYWAY REPAIR REQUIRED before running this:
--   mvn flyway:repair
-- (removes the failed V58 entry from flyway_schema_history)
--
-- This script starts with a DROP block to clean up any partial tables
-- left behind by previous failed runs, then creates everything fresh.
-- Safe to run on a clean DB — all DROPs use IF EXISTS.

-- ── Clean up any partial tables from previous failed attempts ─────────────────
-- Drop in reverse FK dependency order
DROP INDEX IF EXISTS idx_acc_fixed_assets;
DROP INDEX IF EXISTS idx_acc_coa_client;
DROP INDEX IF EXISTS idx_acc_workpaper_audit;
DROP INDEX IF EXISTS idx_acc_workpaper_files;
DROP INDEX IF EXISTS idx_acc_fee_notes_status;
DROP INDEX IF EXISTS idx_acc_time_entries_unbilled;
DROP INDEX IF EXISTS idx_acc_time_entries_client;
DROP INDEX IF EXISTS idx_acc_journal_account;
DROP INDEX IF EXISTS idx_acc_journal_lines;
DROP INDEX IF EXISTS idx_acc_journals_client;
DROP INDEX IF EXISTS idx_acc_deadlines_reminders;
DROP INDEX IF EXISTS idx_acc_deadlines_status;
DROP INDEX IF EXISTS idx_acc_deadlines_client;
DROP INDEX IF EXISTS idx_acc_clients_risk;
DROP INDEX IF EXISTS idx_acc_clients_entity_type;
DROP INDEX IF EXISTS idx_acc_clients_tenant;

DROP TABLE IF EXISTS acc_fixed_assets             CASCADE;
DROP TABLE IF EXISTS acc_bank_recons              CASCADE;
DROP TABLE IF EXISTS acc_journal_lines            CASCADE;
DROP TABLE IF EXISTS acc_journals                 CASCADE;
DROP TABLE IF EXISTS acc_periods                  CASCADE;
DROP TABLE IF EXISTS acc_coa_accounts             CASCADE;
DROP TABLE IF EXISTS acc_tcs_records              CASCADE;
DROP TABLE IF EXISTS acc_public_holidays          CASCADE;
DROP TABLE IF EXISTS acc_tax_deadlines            CASCADE;
DROP TABLE IF EXISTS acc_onboarding_items         CASCADE;
DROP TABLE IF EXISTS acc_client_contacts          CASCADE;
DROP TABLE IF EXISTS acc_client_notes             CASCADE;
DROP TABLE IF EXISTS acc_payments_received        CASCADE;
DROP TABLE IF EXISTS acc_fee_note_lines           CASCADE;
DROP TABLE IF EXISTS acc_fee_notes                CASCADE;
DROP TABLE IF EXISTS acc_billing_rates            CASCADE;
DROP TABLE IF EXISTS acc_time_entries             CASCADE;
DROP TABLE IF EXISTS acc_fica_documents           CASCADE;
DROP TABLE IF EXISTS acc_document_requests        CASCADE;
DROP TABLE IF EXISTS acc_workpaper_audit          CASCADE;
DROP TABLE IF EXISTS acc_workpaper_files          CASCADE;
DROP TABLE IF EXISTS acc_workpaper_folders        CASCADE;
DROP TABLE IF EXISTS acc_engagement_letters       CASCADE;
DROP TABLE IF EXISTS acc_clients                  CASCADE;
DROP TABLE IF EXISTS accountant_profiles          CASCADE;

-- ═══════════════════════════════════════════════════════════════════════════════
-- L1 — PRACTICE SHELL
-- ═══════════════════════════════════════════════════════════════════════════════

CREATE TABLE accountant_profiles (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    firm_name           VARCHAR(200) NOT NULL,
    practice_number     VARCHAR(50),
    registration_number VARCHAR(20),
    vat_number          VARCHAR(15),
    contact_email       VARCHAR(200) NOT NULL,
    contact_phone       VARCHAR(30),
    address             JSONB,
    vat_category        VARCHAR(2) DEFAULT 'A'
        CHECK (vat_category IN ('A','B','C','E')),
    default_hourly_rate NUMERIC(10,2) NOT NULL DEFAULT 750,
    year_end_month      INTEGER NOT NULL DEFAULT 2
        CHECK (year_end_month BETWEEN 1 AND 12),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id)
);

-- ═══════════════════════════════════════════════════════════════════════════════
-- L2 — CLIENT PORTFOLIO
-- ═══════════════════════════════════════════════════════════════════════════════

CREATE TABLE acc_clients (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL REFERENCES tenants(id),
    entity_type             VARCHAR(20) NOT NULL
        CHECK (entity_type IN (
            'PTY_LTD','CC','SOLE_PROP','TRUST','NPO',
            'INDIVIDUAL','PARTNERSHIP','FOREIGN','ARTIST','TRADER','OTHER'
        )),
    trading_name            VARCHAR(200) NOT NULL,
    registered_name         VARCHAR(200),
    registration_number     VARCHAR(30),
    tax_reference_number    VARCHAR(15),
    vat_number              VARCHAR(15),
    income_tax_number       VARCHAR(15),
    vat_category            VARCHAR(2)
        CHECK (vat_category IN ('A','B','C','E')),
    year_end_month          INTEGER NOT NULL DEFAULT 2
        CHECK (year_end_month BETWEEN 1 AND 12),
    risk_rating             VARCHAR(10) NOT NULL DEFAULT 'LOW'
        CHECK (risk_rating IN ('LOW','MEDIUM','HIGH')),
    fica_completed          BOOLEAN NOT NULL DEFAULT false,
    fica_completed_date     DATE,
    sars_agent_appointed    BOOLEAN NOT NULL DEFAULT false,
    sars_agent_date         DATE,
    tcs_pin                 VARCHAR(20),
    tcs_pin_expiry          DATE,
    cipc_anniversary_date   DATE,
    cipc_last_return_date   DATE,
    onboarding_status       VARCHAR(20) NOT NULL DEFAULT 'NEW'
        CHECK (onboarding_status IN ('NEW','IN_PROGRESS','COMPLETE')),
    crm_customer_id         UUID,
    linked_tenant_id        UUID REFERENCES tenants(id),
    contact_email           VARCHAR(200),
    contact_phone           VARCHAR(30),
    active                  BOOLEAN NOT NULL DEFAULT true,
    deleted_at              TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE acc_client_notes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    client_id   UUID NOT NULL REFERENCES acc_clients(id) ON DELETE CASCADE,
    pinned      BOOLEAN NOT NULL DEFAULT false,
    note        TEXT NOT NULL,
    created_by  UUID,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE acc_client_contacts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL REFERENCES acc_clients(id) ON DELETE CASCADE,
    role            VARCHAR(50) NOT NULL,
    full_name       VARCHAR(200) NOT NULL,
    id_number       VARCHAR(13),
    email           VARCHAR(200),
    phone           VARCHAR(30),
    percentage_held NUMERIC(5,2),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE acc_onboarding_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL REFERENCES acc_clients(id) ON DELETE CASCADE,
    item_key        VARCHAR(50) NOT NULL,
    label           VARCHAR(200) NOT NULL,
    completed       BOOLEAN NOT NULL DEFAULT false,
    completed_at    TIMESTAMP,
    completed_by    UUID,
    notes           TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ═══════════════════════════════════════════════════════════════════════════════
-- L4 — SARS TAX CALENDAR & COMPLIANCE ENGINE
-- ═══════════════════════════════════════════════════════════════════════════════

CREATE TABLE acc_tax_deadlines (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    client_id           UUID NOT NULL REFERENCES acc_clients(id) ON DELETE CASCADE,
    deadline_type       VARCHAR(20) NOT NULL
        CHECK (deadline_type IN (
            'VAT201','ITR14','ITR12','IRP6_P1','IRP6_P2','IRP6_P3',
            'EMP201','EMP501','CIPC_RETURN','OTHER'
        )),
    period_year         INTEGER NOT NULL,
    period_month        INTEGER,
    statutory_due_date  DATE NOT NULL,
    adjusted_due_date   DATE NOT NULL,
    status              VARCHAR(10) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','FILED','OVERDUE','WAIVED')),
    filed_date          DATE,
    sars_reference      VARCHAR(50),
    filing_amount       NUMERIC(15,2),
    penalty_amount      NUMERIC(15,2),
    notes               TEXT,
    reminder_30_sent    BOOLEAN NOT NULL DEFAULT false,
    reminder_7_sent     BOOLEAN NOT NULL DEFAULT false,
    reminder_1_sent     BOOLEAN NOT NULL DEFAULT false,
    overdue_flagged_at  TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (client_id, deadline_type, period_year, period_month)
);

CREATE TABLE acc_public_holidays (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    holiday_date DATE NOT NULL UNIQUE,
    name         VARCHAR(100) NOT NULL,
    recurring    BOOLEAN NOT NULL DEFAULT true
);

INSERT INTO acc_public_holidays (holiday_date, name) VALUES
    ('2026-01-01', 'New Year''s Day'),
    ('2026-03-21', 'Human Rights Day'),
    ('2026-04-03', 'Good Friday'),
    ('2026-04-06', 'Family Day'),
    ('2026-04-27', 'Freedom Day'),
    ('2026-05-01', 'Workers'' Day'),
    ('2026-06-16', 'Youth Day'),
    ('2026-08-09', 'Women''s Day'),
    ('2026-08-10', 'Women''s Day observed'),
    ('2026-09-24', 'Heritage Day'),
    ('2026-12-16', 'Day of Reconciliation'),
    ('2026-12-25', 'Christmas Day'),
    ('2026-12-26', 'Day of Goodwill')
ON CONFLICT DO NOTHING;

CREATE TABLE acc_tcs_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL REFERENCES acc_clients(id) ON DELETE CASCADE,
    pin             VARCHAR(20) NOT NULL,
    issued_date     DATE,
    expiry_date     DATE,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE','EXPIRED','CANCELLED')),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ═══════════════════════════════════════════════════════════════════════════════
-- L3 — ACCOUNTING CORE
-- ═══════════════════════════════════════════════════════════════════════════════

CREATE TABLE acc_coa_accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL REFERENCES acc_clients(id) ON DELETE CASCADE,
    account_code    VARCHAR(20) NOT NULL,
    account_name    VARCHAR(200) NOT NULL,
    account_type    VARCHAR(20) NOT NULL
        CHECK (account_type IN ('ASSET','LIABILITY','EQUITY','INCOME','EXPENSE')),
    sub_type        VARCHAR(50),
    vat_applicable  BOOLEAN NOT NULL DEFAULT false,
    vat_type        VARCHAR(10)
        CHECK (vat_type IN ('OUTPUT','INPUT','EXEMPT','ZERO_RATED') OR vat_type IS NULL),
    tax_schedule    VARCHAR(30),
    parent_id       UUID REFERENCES acc_coa_accounts(id),
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (client_id, account_code)
);

CREATE TABLE acc_periods (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL REFERENCES acc_clients(id) ON DELETE CASCADE,
    period_year     INTEGER NOT NULL,
    period_month    INTEGER NOT NULL CHECK (period_month BETWEEN 1 AND 12),
    status          VARCHAR(15) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN','CLOSED','LOCKED')),
    closed_at       TIMESTAMP,
    closed_by       UUID,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (client_id, period_year, period_month)
);

CREATE TABLE acc_journals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL REFERENCES acc_clients(id) ON DELETE CASCADE,
    period_id       UUID NOT NULL REFERENCES acc_periods(id),
    reference       VARCHAR(50) NOT NULL,
    description     TEXT NOT NULL,
    journal_type    VARCHAR(20) NOT NULL DEFAULT 'STANDARD'
        CHECK (journal_type IN ('STANDARD','ADJUSTING','REVERSING','OPENING','CLOSING','VAT')),
    status          VARCHAR(15) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','PREPARED','REVIEWED','APPROVED','POSTED','REVERSED')),
    prepared_by     UUID,
    prepared_at     TIMESTAMP,
    reviewed_by     UUID,
    reviewed_at     TIMESTAMP,
    approved_by     UUID,
    approved_at     TIMESTAMP,
    posted_at       TIMESTAMP,
    reversed_by_journal_id UUID,
    journal_date    DATE NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE acc_journal_lines (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    journal_id      UUID NOT NULL REFERENCES acc_journals(id) ON DELETE CASCADE,
    account_id      UUID NOT NULL REFERENCES acc_coa_accounts(id),
    description     VARCHAR(500),
    debit           NUMERIC(15,2) NOT NULL DEFAULT 0,
    credit          NUMERIC(15,2) NOT NULL DEFAULT 0,
    vat_amount      NUMERIC(15,2),
    vat_type        VARCHAR(10),
    line_order      INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT chk_debit_credit CHECK (
        (debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0)
    )
);

CREATE TABLE acc_bank_recons (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    client_id           UUID NOT NULL REFERENCES acc_clients(id) ON DELETE CASCADE,
    account_id          UUID NOT NULL REFERENCES acc_coa_accounts(id),
    period_year         INTEGER NOT NULL,
    period_month        INTEGER NOT NULL,
    statement_balance   NUMERIC(15,2) NOT NULL,
    gl_balance          NUMERIC(15,2) NOT NULL,
    unreconciled_items  INTEGER NOT NULL DEFAULT 0,
    status              VARCHAR(10) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN','RECONCILED')),
    reconciled_at       TIMESTAMP,
    reconciled_by       UUID,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (client_id, account_id, period_year, period_month)
);

CREATE TABLE acc_fixed_assets (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    client_id               UUID NOT NULL REFERENCES acc_clients(id) ON DELETE CASCADE,
    asset_code              VARCHAR(30) NOT NULL,
    description             VARCHAR(300) NOT NULL,
    sars_category           VARCHAR(20),
    cost                    NUMERIC(15,2) NOT NULL,
    acquisition_date        DATE NOT NULL,
    useful_life_years       INTEGER,
    depreciation_method     VARCHAR(20) NOT NULL DEFAULT 'STRAIGHT_LINE'
        CHECK (depreciation_method IN ('STRAIGHT_LINE','REDUCING_BALANCE','NONE')),
    accumulated_depreciation NUMERIC(15,2) NOT NULL DEFAULT 0,
    disposal_date           DATE,
    disposal_proceeds       NUMERIC(15,2),
    disposal_gain_loss      NUMERIC(15,2),
    account_id              UUID REFERENCES acc_coa_accounts(id),
    active                  BOOLEAN NOT NULL DEFAULT true,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (client_id, asset_code)
);

-- ═══════════════════════════════════════════════════════════════════════════════
-- L5 — WORKPAPER & ENGAGEMENT MANAGEMENT
-- ═══════════════════════════════════════════════════════════════════════════════

CREATE TABLE acc_engagement_letters (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL REFERENCES acc_clients(id) ON DELETE CASCADE,
    engagement_year INTEGER NOT NULL,
    template_body   TEXT,
    customised_body TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','SENT','SIGNED','EXPIRED','RENEWED')),
    sent_at         TIMESTAMP,
    signed_at       TIMESTAMP,
    expires_at      TIMESTAMP,
    signing_token   VARCHAR(512),
    contract_id     UUID,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (client_id, engagement_year)
);

CREATE TABLE acc_workpaper_folders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL REFERENCES acc_clients(id) ON DELETE CASCADE,
    parent_id       UUID REFERENCES acc_workpaper_folders(id),
    engagement_year INTEGER NOT NULL,
    name            VARCHAR(200) NOT NULL,
    folder_type     VARCHAR(20)
        CHECK (folder_type IN ('TB','RECONS','TAX','FS','FICA','GENERAL') OR folder_type IS NULL),
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE acc_workpaper_files (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL REFERENCES acc_clients(id) ON DELETE CASCADE,
    folder_id       UUID NOT NULL REFERENCES acc_workpaper_folders(id),
    file_name       VARCHAR(300) NOT NULL,
    storage_key     VARCHAR(500),
    mime_type       VARCHAR(100),
    file_size_bytes BIGINT,
    review_status   VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (review_status IN ('DRAFT','PREPARED','REVIEWED','SIGNED_OFF')),
    prepared_by     UUID,
    prepared_at     TIMESTAMP,
    reviewed_by     UUID,
    reviewed_at     TIMESTAMP,
    signed_off_by   UUID,
    signed_off_at   TIMESTAMP,
    version_number  INTEGER NOT NULL DEFAULT 1,
    superseded_by   UUID REFERENCES acc_workpaper_files(id),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE acc_workpaper_audit (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    file_id         UUID NOT NULL REFERENCES acc_workpaper_files(id),
    event_type      VARCHAR(20) NOT NULL
        CHECK (event_type IN ('UPLOADED','VIEWED','DOWNLOADED','STATUS_CHANGED','DELETED','RESTORED')),
    performed_by    UUID,
    performed_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    metadata        JSONB
);

CREATE TABLE acc_document_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL REFERENCES acc_clients(id) ON DELETE CASCADE,
    folder_id       UUID REFERENCES acc_workpaper_folders(id),
    requested_by    UUID,
    description     TEXT NOT NULL,
    items           JSONB NOT NULL DEFAULT '[]',
    status          VARCHAR(15) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','PARTIAL','COMPLETE','CANCELLED')),
    due_date        DATE,
    completed_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE acc_fica_documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL REFERENCES acc_clients(id) ON DELETE CASCADE,
    doc_type        VARCHAR(30) NOT NULL
        CHECK (doc_type IN ('ID_COPY','PROOF_OF_ADDRESS','BENEFICIAL_OWNERSHIP',
                            'COMPANY_DOCUMENTS','TRUST_DEED','OTHER')),
    file_name       VARCHAR(300),
    storage_key     VARCHAR(500),
    verified        BOOLEAN NOT NULL DEFAULT false,
    verified_by     UUID,
    verified_at     TIMESTAMP,
    expiry_date     DATE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ═══════════════════════════════════════════════════════════════════════════════
-- L6 — BILLING & TIME TRACKING
-- ═══════════════════════════════════════════════════════════════════════════════

CREATE TABLE acc_billing_rates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL REFERENCES acc_clients(id) ON DELETE CASCADE,
    activity_type   VARCHAR(50) NOT NULL,
    hourly_rate     NUMERIC(10,2) NOT NULL,
    effective_from  DATE NOT NULL DEFAULT CURRENT_DATE,
    effective_to    DATE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (client_id, activity_type, effective_from)
);

CREATE TABLE acc_time_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL REFERENCES acc_clients(id) ON DELETE CASCADE,
    practitioner_id UUID,
    entry_date      DATE NOT NULL,
    activity_type   VARCHAR(50) NOT NULL,
    description     TEXT,
    hours           NUMERIC(5,2) NOT NULL CHECK (hours > 0 AND hours <= 24),
    hourly_rate     NUMERIC(10,2) NOT NULL,
    billable        BOOLEAN NOT NULL DEFAULT true,
    status          VARCHAR(15) NOT NULL DEFAULT 'UNBILLED'
        CHECK (status IN ('UNBILLED','BILLED','WRITTEN_OFF','NON_BILLABLE')),
    invoice_id      UUID,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE acc_fee_notes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL REFERENCES acc_clients(id) ON DELETE CASCADE,
    invoice_number  VARCHAR(30) NOT NULL,
    invoice_date    DATE NOT NULL,
    due_date        DATE NOT NULL,
    subtotal        NUMERIC(15,2) NOT NULL,
    vat_amount      NUMERIC(15,2) NOT NULL DEFAULT 0,
    total           NUMERIC(15,2) NOT NULL,
    status          VARCHAR(15) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','SENT','PARTIAL','PAID','OVERDUE','WRITTEN_OFF')),
    recurring       BOOLEAN NOT NULL DEFAULT false,
    recurrence_day  INTEGER,
    fixed_fee       NUMERIC(15,2),
    notes           TEXT,
    sent_at         TIMESTAMP,
    paid_at         TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, invoice_number)
);

CREATE TABLE acc_fee_note_lines (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fee_note_id     UUID NOT NULL REFERENCES acc_fee_notes(id) ON DELETE CASCADE,
    description     VARCHAR(500) NOT NULL,
    quantity        NUMERIC(8,2) NOT NULL DEFAULT 1,
    unit_price      NUMERIC(10,2) NOT NULL,
    vat_rate        NUMERIC(5,2) NOT NULL DEFAULT 15,
    amount          NUMERIC(15,2) NOT NULL,
    time_entry_id   UUID REFERENCES acc_time_entries(id),
    line_order      INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE acc_payments_received (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL REFERENCES acc_clients(id) ON DELETE CASCADE,
    fee_note_id     UUID NOT NULL REFERENCES acc_fee_notes(id),
    amount          NUMERIC(15,2) NOT NULL,
    payment_date    DATE NOT NULL,
    payment_method  VARCHAR(30),
    reference       VARCHAR(100),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ═══════════════════════════════════════════════════════════════════════════════
-- INDEXES
-- ═══════════════════════════════════════════════════════════════════════════════

CREATE INDEX idx_acc_clients_tenant       ON acc_clients(tenant_id)              WHERE deleted_at IS NULL;
CREATE INDEX idx_acc_clients_entity_type  ON acc_clients(tenant_id, entity_type) WHERE deleted_at IS NULL;
CREATE INDEX idx_acc_clients_risk         ON acc_clients(tenant_id, risk_rating)  WHERE deleted_at IS NULL;
CREATE INDEX idx_acc_deadlines_client     ON acc_tax_deadlines(client_id, deadline_type, period_year);
CREATE INDEX idx_acc_deadlines_status     ON acc_tax_deadlines(tenant_id, status, adjusted_due_date);
CREATE INDEX idx_acc_deadlines_reminders  ON acc_tax_deadlines(adjusted_due_date, status)
    WHERE status = 'PENDING' AND (NOT reminder_30_sent OR NOT reminder_7_sent OR NOT reminder_1_sent);
CREATE INDEX idx_acc_journals_client      ON acc_journals(client_id, period_id)   WHERE status = 'POSTED';
CREATE INDEX idx_acc_journal_lines        ON acc_journal_lines(journal_id);
CREATE INDEX idx_acc_journal_account      ON acc_journal_lines(account_id);
CREATE INDEX idx_acc_time_entries_client  ON acc_time_entries(client_id, entry_date);
CREATE INDEX idx_acc_time_entries_unbilled ON acc_time_entries(tenant_id, status)  WHERE status = 'UNBILLED';
CREATE INDEX idx_acc_fee_notes_status     ON acc_fee_notes(tenant_id, status, due_date);
CREATE INDEX idx_acc_workpaper_files      ON acc_workpaper_files(folder_id, review_status);
CREATE INDEX idx_acc_workpaper_audit      ON acc_workpaper_audit(file_id, performed_at);
CREATE INDEX idx_acc_coa_client           ON acc_coa_accounts(client_id, account_type) WHERE active = true;
CREATE INDEX idx_acc_fixed_assets         ON acc_fixed_assets(client_id)          WHERE active = true;
