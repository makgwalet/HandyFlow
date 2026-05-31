-- WHY? Double-entry bookkeeping for South African SMEs.
-- Compliant with IFRS for SMEs and SARS VAT requirements.
-- Chart of accounts follows SA standard numbering (1xxx assets, 2xxx liabilities, etc.)

-- Chart of accounts
CREATE TABLE acc_accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    account_code    VARCHAR(20) NOT NULL,
    account_name    VARCHAR(200) NOT NULL,
    account_type    VARCHAR(20) NOT NULL
        CHECK (account_type IN ('ASSET','LIABILITY','EQUITY','INCOME','EXPENSE')),
    account_subtype VARCHAR(50),
    parent_id       UUID REFERENCES acc_accounts(id),
    is_system       BOOLEAN NOT NULL DEFAULT false,
    active          BOOLEAN NOT NULL DEFAULT true,
    opening_balance NUMERIC(15,2) NOT NULL DEFAULT 0,
    description     TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, account_code)
);

-- Journal entries (header)
CREATE TABLE acc_journal_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    entry_number    VARCHAR(20) NOT NULL,
    entry_date      DATE NOT NULL,
    description     TEXT NOT NULL,
    reference       VARCHAR(100),
    entry_type      VARCHAR(30) NOT NULL DEFAULT 'MANUAL'
        CHECK (entry_type IN ('MANUAL','INVOICE','PAYMENT','BANK','DEPRECIATION','ADJUSTMENT','VAT')),
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','POSTED','REVERSED')),
    total_debit     NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_credit    NUMERIC(15,2) NOT NULL DEFAULT 0,
    posted_at       TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, entry_number)
);

-- Journal lines (double-entry)
CREATE TABLE acc_journal_lines (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    journal_entry_id UUID NOT NULL REFERENCES acc_journal_entries(id),
    account_id      UUID NOT NULL REFERENCES acc_accounts(id),
    description     TEXT,
    debit_amount    NUMERIC(15,2) NOT NULL DEFAULT 0,
    credit_amount   NUMERIC(15,2) NOT NULL DEFAULT 0,
    sort_order      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_journal_line_amounts CHECK (
        (debit_amount > 0 AND credit_amount = 0) OR
        (credit_amount > 0 AND debit_amount = 0)
    )
);

-- Bank accounts
CREATE TABLE acc_bank_accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    account_id      UUID REFERENCES acc_accounts(id),
    bank_name       VARCHAR(100) NOT NULL,
    account_name    VARCHAR(200) NOT NULL,
    account_number  VARCHAR(50) NOT NULL,
    branch_code     VARCHAR(10),
    account_type    VARCHAR(20) DEFAULT 'CURRENT'
        CHECK (account_type IN ('CURRENT','SAVINGS','CREDIT')),
    currency        VARCHAR(3) NOT NULL DEFAULT 'ZAR',
    current_balance NUMERIC(15,2) NOT NULL DEFAULT 0,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP,
    version         BIGINT NOT NULL DEFAULT 0
);

-- Bank transactions
CREATE TABLE acc_bank_transactions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    bank_account_id     UUID NOT NULL REFERENCES acc_bank_accounts(id),
    transaction_date    DATE NOT NULL,
    description         TEXT NOT NULL,
    reference           VARCHAR(100),
    amount              NUMERIC(15,2) NOT NULL,
    transaction_type    VARCHAR(10) NOT NULL CHECK (transaction_type IN ('DEBIT','CREDIT')),
    balance_after       NUMERIC(15,2),
    reconciled          BOOLEAN NOT NULL DEFAULT false,
    reconciled_at       TIMESTAMP,
    journal_line_id     UUID REFERENCES acc_journal_lines(id),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- VAT periods
CREATE TABLE acc_vat_periods (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN','CLOSED','SUBMITTED')),
    output_vat      NUMERIC(15,2) NOT NULL DEFAULT 0,
    input_vat       NUMERIC(15,2) NOT NULL DEFAULT 0,
    vat_payable     NUMERIC(15,2) GENERATED ALWAYS AS (output_vat - input_vat) STORED,
    submitted_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Seed standard SA chart of accounts function
-- WHY? Every new tenant needs a standard chart of accounts to start bookkeeping.
-- We seed on first use via the AccountingService, not here (tenant-specific).

-- Indexes
CREATE INDEX idx_acc_accounts_tenant       ON acc_accounts(tenant_id) WHERE active = true;
CREATE INDEX idx_acc_journal_entries_tenant ON acc_journal_entries(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_acc_journal_entries_date  ON acc_journal_entries(tenant_id, entry_date);
CREATE INDEX idx_acc_journal_lines_entry   ON acc_journal_lines(journal_entry_id);
CREATE INDEX idx_acc_journal_lines_account ON acc_journal_lines(account_id);
CREATE INDEX idx_acc_bank_transactions_account ON acc_bank_transactions(bank_account_id);
CREATE INDEX idx_acc_bank_transactions_date    ON acc_bank_transactions(tenant_id, transaction_date);