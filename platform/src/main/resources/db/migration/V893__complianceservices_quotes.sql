-- Compliance Services: quotes for a client. A quote is priced on one numbered price book version, pinned when the
-- quote is started, and on the approved service versions in force. The server works every figure out and stores
-- it, so an accepted quote shows what the client was given even if prices change later. A discount above the
-- consultant limit needs an administrator, who is not the person who wrote the quote unless they record why.

CREATE TABLE IF NOT EXISTS compliance_quotes (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    client_id          UUID NOT NULL REFERENCES compliance_service_clients(id),
    quote_number       VARCHAR(30) NOT NULL,
    title              VARCHAR(255),
    status             VARCHAR(20) NOT NULL DEFAULT 'DRAFT',     -- DRAFT, PENDING_APPROVAL, APPROVED, SENT, ACCEPTED, DECLINED, WITHDRAWN
    price_book_version INTEGER NOT NULL,
    currency           VARCHAR(3) NOT NULL DEFAULT 'ZAR',
    valid_until        DATE,
    notes              TEXT,
    terms              TEXT,
    required_approver  VARCHAR(40) NOT NULL DEFAULT 'CONSULTANT',
    max_discount_pct   NUMERIC(5,2) NOT NULL DEFAULT 0,
    professional       NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount_total     NUMERIC(14,2) NOT NULL DEFAULT 0,
    disbursements      NUMERIC(14,2) NOT NULL DEFAULT 0,
    vat                NUMERIC(14,2) NOT NULL DEFAULT 0,
    vat_rate_pct       NUMERIC(5,2) NOT NULL DEFAULT 0,
    total              NUMERIC(14,2) NOT NULL DEFAULT 0,
    created_by         UUID,
    submitted_by       UUID,
    submitted_at       TIMESTAMPTZ,
    approved_by        UUID,
    approved_at        TIMESTAMPTZ,
    approval_note      TEXT,
    approval_exception_reason TEXT,
    sent_by            UUID,
    sent_at            TIMESTAMPTZ,
    decided_by         UUID,
    decided_at         TIMESTAMPTZ,
    decision_note      TEXT,
    withdrawn_reason   TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_compliance_quote_number UNIQUE (tenant_id, quote_number),
    CONSTRAINT ck_compliance_quote_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'SENT', 'ACCEPTED', 'DECLINED', 'WITHDRAWN')),
    CONSTRAINT ck_compliance_quote_approver CHECK (required_approver IN ('CONSULTANT', 'COMPLIANCE_MANAGER', 'PRACTICE_OWNER', 'PRACTICE_OWNER_WITH_EXCEPTION')),
    CONSTRAINT ck_compliance_quote_amounts CHECK (professional >= 0 AND discount_total >= 0 AND disbursements >= 0 AND vat >= 0 AND total >= 0
        AND max_discount_pct >= 0 AND max_discount_pct <= 100),
    CONSTRAINT ck_compliance_quote_sent CHECK (status NOT IN ('SENT', 'ACCEPTED', 'DECLINED') OR sent_at IS NOT NULL),
    CONSTRAINT ck_compliance_quote_decided CHECK (status NOT IN ('ACCEPTED', 'DECLINED') OR decided_at IS NOT NULL),
    CONSTRAINT ck_compliance_quote_valid CHECK (status IN ('DRAFT', 'WITHDRAWN') OR valid_until IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_compliance_quotes_client ON compliance_quotes(tenant_id, client_id);
CREATE INDEX IF NOT EXISTS idx_compliance_quotes_status ON compliance_quotes(tenant_id, status);

CREATE TABLE IF NOT EXISTS compliance_quote_lines (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    quote_id          UUID NOT NULL REFERENCES compliance_quotes(id) ON DELETE CASCADE,
    service_code      VARCHAR(60) NOT NULL,
    service_name      VARCHAR(255),
    service_version   INTEGER,
    pricing_model     VARCHAR(20) NOT NULL,
    description       VARCHAR(500),
    units             NUMERIC(10,2),
    hours             NUMERIC(8,2),
    complexity_pct    NUMERIC(5,2),
    urgent            BOOLEAN NOT NULL DEFAULT FALSE,
    discount_pct      NUMERIC(5,2) NOT NULL DEFAULT 0,
    before_discount   NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount          NUMERIC(14,2) NOT NULL DEFAULT 0,
    professional      NUMERIC(14,2) NOT NULL DEFAULT 0,
    disbursements     NUMERIC(14,2) NOT NULL DEFAULT 0,
    vat               NUMERIC(14,2) NOT NULL DEFAULT 0,
    total             NUMERIC(14,2) NOT NULL DEFAULT 0,
    breakdown         TEXT,
    sort_order        INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_quote_line_discount CHECK (discount_pct >= 0 AND discount_pct <= 100),
    CONSTRAINT ck_quote_line_amounts CHECK (before_discount >= 0 AND discount >= 0 AND professional >= 0 AND disbursements >= 0 AND vat >= 0 AND total >= 0)
);
CREATE INDEX IF NOT EXISTS idx_compliance_quote_lines ON compliance_quote_lines(quote_id);

CREATE TABLE IF NOT EXISTS compliance_quote_adjustments (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    line_id     UUID NOT NULL REFERENCES compliance_quote_lines(id) ON DELETE CASCADE,
    kind        VARCHAR(12) NOT NULL,                       -- EXTRA, DISBURSEMENT
    description VARCHAR(200) NOT NULL,
    quantity    NUMERIC(10,2),
    amount      NUMERIC(14,2) NOT NULL,                     -- price each for an extra, the cost for a disbursement
    vatable     BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_quote_adjustment_kind CHECK (kind IN ('EXTRA', 'DISBURSEMENT')),
    CONSTRAINT ck_quote_adjustment_amount CHECK (amount >= 0 AND (quantity IS NULL OR quantity > 0))
);
CREATE INDEX IF NOT EXISTS idx_compliance_quote_adjustments ON compliance_quote_adjustments(line_id);
