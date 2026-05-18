-- src/main/resources/db/migration/V8__create_invoicing.sql

CREATE TABLE quotes (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    customer_id     UUID        NOT NULL,
    quote_number    VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    title           VARCHAR(255),
    notes           TEXT,
    -- WHY store subtotal, vat, total separately?
    -- VAT reporting requires knowing VAT amount independently.
    -- Recalculating totals from line items is expensive at query time.
    subtotal        NUMERIC(15,2) NOT NULL DEFAULT 0,
    vat_total       NUMERIC(15,2) NOT NULL DEFAULT 0,
    total           NUMERIC(15,2) NOT NULL DEFAULT 0,
    currency        VARCHAR(3)  NOT NULL DEFAULT 'ZAR',
    sent_at         TIMESTAMP,
    expires_at      TIMESTAMP,  -- set to sent_at + 30 days when SENT
    accepted_at     TIMESTAMP,
    rejected_at     TIMESTAMP,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMP,
    deleted_by      UUID,
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_quotes PRIMARY KEY (id),
    CONSTRAINT uq_quotes_tenant_number UNIQUE (tenant_id, quote_number),
    CONSTRAINT chk_quotes_status CHECK (
        status IN ('DRAFT','SENT','ACCEPTED','REJECTED','EXPIRED','INVOICED')
    )
);

CREATE TABLE quote_line_items (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    quote_id            UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    -- WHY nullable catalogue_item_id?
    -- Allows free-typed one-off items without forcing catalogue entry
    catalogue_item_id   UUID,
    description         VARCHAR(500)    NOT NULL,
    unit                VARCHAR(50)     NOT NULL DEFAULT 'each',
    quantity            NUMERIC(15,2)   NOT NULL,
    unit_price          NUMERIC(15,2)   NOT NULL,
    vat_rate            NUMERIC(5,2)    NOT NULL DEFAULT 15.00,
    line_total          NUMERIC(15,2)   NOT NULL,
    vat_amount          NUMERIC(15,2)   NOT NULL,
    sort_order          INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT pk_quote_line_items PRIMARY KEY (id),
    CONSTRAINT fk_qli_quote
        FOREIGN KEY (quote_id) REFERENCES quotes(id) ON DELETE CASCADE,
    CONSTRAINT fk_qli_catalogue
        FOREIGN KEY (catalogue_item_id)
        REFERENCES catalogue_items(id) ON DELETE SET NULL
);

CREATE TABLE invoices (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    customer_id     UUID        NOT NULL,
    quote_id        UUID,       -- nullable: invoice can exist without a quote
    invoice_number  VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    title           VARCHAR(255),
    notes           TEXT,
    subtotal        NUMERIC(15,2) NOT NULL DEFAULT 0,
    vat_total       NUMERIC(15,2) NOT NULL DEFAULT 0,
    total           NUMERIC(15,2) NOT NULL DEFAULT 0,
    amount_paid     NUMERIC(15,2) NOT NULL DEFAULT 0,
    currency        VARCHAR(3)  NOT NULL DEFAULT 'ZAR',
    due_date        DATE,
    issued_at       TIMESTAMP,
    paid_at         TIMESTAMP,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMP,
    deleted_by      UUID,
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_invoices PRIMARY KEY (id),
    CONSTRAINT uq_invoices_tenant_number UNIQUE (tenant_id, invoice_number),
    CONSTRAINT fk_invoices_quote FOREIGN KEY (quote_id)
        REFERENCES quotes(id) ON DELETE SET NULL,
    CONSTRAINT chk_invoices_status CHECK (
        status IN ('DRAFT','ISSUED','PARTIALLY_PAID','PAID','OVERDUE','CANCELLED')
    )
);

CREATE TABLE invoice_line_items (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    invoice_id          UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    catalogue_item_id   UUID,
    description         VARCHAR(500)    NOT NULL,
    unit                VARCHAR(50)     NOT NULL DEFAULT 'each',
    quantity            NUMERIC(15,2)   NOT NULL,
    unit_price          NUMERIC(15,2)   NOT NULL,
    vat_rate            NUMERIC(5,2)    NOT NULL DEFAULT 15.00,
    line_total          NUMERIC(15,2)   NOT NULL,
    vat_amount          NUMERIC(15,2)   NOT NULL,
    sort_order          INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT pk_invoice_line_items PRIMARY KEY (id),
    CONSTRAINT fk_ili_invoice
        FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE,
    CONSTRAINT fk_ili_catalogue
        FOREIGN KEY (catalogue_item_id)
        REFERENCES catalogue_items(id) ON DELETE SET NULL
);

CREATE INDEX idx_quotes_tenant ON quotes(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_quotes_customer ON quotes(customer_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_quotes_expires ON quotes(expires_at)
    WHERE status = 'SENT' AND expires_at IS NOT NULL;
-- WHY this index? The expiry scheduler uses this exact query daily.
-- Only SENT quotes can expire — partial index keeps it tiny and fast.

CREATE INDEX idx_invoices_tenant ON invoices(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_invoices_customer ON invoices(customer_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_invoices_status ON invoices(tenant_id, status) WHERE deleted_at IS NULL;