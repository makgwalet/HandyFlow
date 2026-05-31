-- V33__accounts_payable.sql
-- Accounts Payable module — track supplier bills, EFT batches, payments.
-- WHY is_supplier on customers? Reuse CRM contacts as suppliers.
-- Same company can be both a customer (you sell to them) and supplier
-- (you buy from them). Avoids a 4th supplier table.

ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS is_supplier     BOOLEAN     NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS supplier_notes  TEXT,
    ADD COLUMN IF NOT EXISTS bank_name       VARCHAR(100),
    ADD COLUMN IF NOT EXISTS bank_account    VARCHAR(50),
    ADD COLUMN IF NOT EXISTS bank_branch     VARCHAR(10),
    ADD COLUMN IF NOT EXISTS payment_terms   VARCHAR(100);  -- e.g. "Net 30"

-- ── Bills received from suppliers ─────────────────────────────────────────────
-- WHY attachment_url and pop_url separate?
-- attachment_url = the supplier's invoice (PDF they sent you)
-- pop_url        = proof of payment (POP) after you pay them
-- Both are base64 or storage keys.
CREATE TABLE ap_bills (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    supplier_id     UUID        REFERENCES customers(id) ON DELETE SET NULL,
    supplier_name   VARCHAR(255) NOT NULL,  -- denormalised for display if supplier deleted
    bill_number     VARCHAR(50) NOT NULL,   -- supplier's invoice number
    bill_date       DATE        NOT NULL,
    due_date        DATE        NOT NULL,
    category        VARCHAR(50) NOT NULL DEFAULT 'OTHER'
        CHECK (category IN (
            'RENT','UTILITIES','FUEL','SALARY','PROFESSIONAL_FEES',
            'EQUIPMENT','MAINTENANCE','INSURANCE','SUBSCRIPTIONS',
            'MARKETING','OTHER'
        )),
    description     TEXT        NOT NULL,
    amount          NUMERIC(15,2) NOT NULL,  -- excl. VAT
    vat_amount      NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_amount    NUMERIC(15,2) NOT NULL,  -- amount + vat_amount
    currency        VARCHAR(3)  NOT NULL DEFAULT 'ZAR',
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','APPROVED','PAID','OVERDUE','CANCELLED')),

    -- Accounting integration
    journal_entry_id    UUID,   -- set on approve (expense + AP credit)
    payment_journal_id  UUID,   -- set on pay (AP debit + bank credit)

    -- Evidence uploads
    attachment_url  TEXT,       -- supplier's invoice document (base64 or key)
    attachment_name VARCHAR(255),
    pop_url         TEXT,       -- proof of payment uploaded after paying
    pop_name        VARCHAR(255),
    pop_uploaded_at TIMESTAMP,
    pop_uploaded_by UUID REFERENCES users(id),

    -- Payment tracking
    paid_at         TIMESTAMP,
    paid_by         UUID REFERENCES users(id),
    payment_ref     VARCHAR(100),
    batch_id        UUID,       -- set when added to an EFT batch

    notes           TEXT,
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMP,
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_ap_bills PRIMARY KEY (id),
    CONSTRAINT uq_ap_bills_number UNIQUE (tenant_id, bill_number)
);

CREATE INDEX idx_ap_bills_tenant        ON ap_bills(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_ap_bills_status        ON ap_bills(tenant_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_ap_bills_due_date      ON ap_bills(tenant_id, due_date) WHERE deleted_at IS NULL;
CREATE INDEX idx_ap_bills_supplier      ON ap_bills(supplier_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_ap_bills_batch         ON ap_bills(batch_id) WHERE batch_id IS NOT NULL;

-- ── EFT batches — group bills for bulk bank payment ───────────────────────────
CREATE TABLE ap_eft_batches (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    batch_number    VARCHAR(20) NOT NULL,
    bank_account_id UUID        REFERENCES acc_bank_accounts(id) ON DELETE SET NULL,
    description     TEXT,
    total_amount    NUMERIC(15,2) NOT NULL DEFAULT 0,
    bill_count      INT         NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','SUBMITTED','PAID','CANCELLED')),
    payment_date    DATE,       -- scheduled/actual payment date
    payment_ref     VARCHAR(100),

    -- Evidence
    pop_url         TEXT,       -- bank confirmation / remittance advice
    pop_name        VARCHAR(255),
    pop_uploaded_at TIMESTAMP,
    pop_uploaded_by UUID REFERENCES users(id),

    submitted_at    TIMESTAMP,
    paid_at         TIMESTAMP,
    paid_by         UUID REFERENCES users(id),
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_ap_eft_batches PRIMARY KEY (id),
    CONSTRAINT uq_ap_batch_number UNIQUE (tenant_id, batch_number)
);

CREATE INDEX idx_ap_batches_tenant  ON ap_eft_batches(tenant_id);
CREATE INDEX idx_ap_batches_status  ON ap_eft_batches(tenant_id, status);

-- ── Batch items — bills included in an EFT batch ──────────────────────────────
CREATE TABLE ap_batch_items (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    batch_id    UUID        NOT NULL REFERENCES ap_eft_batches(id) ON DELETE CASCADE,
    bill_id     UUID        NOT NULL REFERENCES ap_bills(id),
    amount      NUMERIC(15,2) NOT NULL,   -- amount being paid (may be partial in future)

    CONSTRAINT pk_ap_batch_items PRIMARY KEY (id),
    CONSTRAINT uq_ap_batch_bill UNIQUE (batch_id, bill_id)  -- bill in one batch only
);

CREATE INDEX idx_ap_batch_items_batch ON ap_batch_items(batch_id);
CREATE INDEX idx_ap_batch_items_bill  ON ap_batch_items(bill_id);
