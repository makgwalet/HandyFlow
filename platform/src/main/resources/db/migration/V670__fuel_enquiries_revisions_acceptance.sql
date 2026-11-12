-- B06a: enquiries, quote revision history and an acceptance record. Additive only; no existing table is changed.
CREATE TABLE fuel_enquiry_sequences (
    tenant_id UUID NOT NULL,
    year      INT  NOT NULL,
    last_seq  INT  NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, year)
);

CREATE TABLE fuel_enquiries (
    id               UUID          PRIMARY KEY,
    tenant_id        UUID          NOT NULL,
    enquiry_number   VARCHAR(20)   NOT NULL,
    account_id       UUID          REFERENCES fuel_customer_accounts (id),
    contact_name     VARCHAR(120),
    contact_phone    VARCHAR(40),
    contact_email    VARCHAR(160),
    fuel_type        VARCHAR(30)   NOT NULL,
    litres           NUMERIC(12,2) NOT NULL CHECK (litres > 0),
    requested_date   DATE,
    source           VARCHAR(10)   NOT NULL DEFAULT 'OTHER' CHECK (source IN ('PHONE','EMAIL','WALK_IN','PORTAL','OTHER')),
    status           VARCHAR(10)   NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW','QUOTED','LOST','CANCELLED')),
    quote_order_id   UUID          REFERENCES fuel_sales_orders (id),
    decision_reason  VARCHAR(300),
    notes            VARCHAR(500),
    created_by       UUID,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    decided_at       TIMESTAMPTZ,
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_fuel_enquiry_quoted CHECK ((status = 'QUOTED') = (quote_order_id IS NOT NULL))
);
CREATE UNIQUE INDEX uq_fuel_enquiry_number ON fuel_enquiries (tenant_id, enquiry_number);
CREATE UNIQUE INDEX uq_fuel_enquiry_quote ON fuel_enquiries (quote_order_id) WHERE quote_order_id IS NOT NULL;
CREATE INDEX ix_fuel_enquiry_tenant ON fuel_enquiries (tenant_id, created_at DESC);

-- Append-only: one row per version of a quotation. Revising a quote adds a row; nothing is updated or deleted.
CREATE TABLE fuel_order_revisions (
    id                 UUID          PRIMARY KEY,
    tenant_id          UUID          NOT NULL,
    order_id           UUID          NOT NULL REFERENCES fuel_sales_orders (id),
    revision_no        INT           NOT NULL,
    site_id            UUID,
    fuel_type          VARCHAR(30)   NOT NULL,
    litres             NUMERIC(12,2) NOT NULL,
    price_per_litre    NUMERIC(10,4) NOT NULL,
    requested_date     DATE,
    quote_valid_until  DATE,
    customer_reference VARCHAR(60),
    notes              VARCHAR(500),
    recorded_by        UUID,
    recorded_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_fuel_order_revision ON fuel_order_revisions (order_id, revision_no);

-- How and by whom the customer accepted the quotation (one per order, never edited).
CREATE TABLE fuel_order_acceptances (
    id             UUID         PRIMARY KEY,
    tenant_id      UUID         NOT NULL,
    order_id       UUID         NOT NULL REFERENCES fuel_sales_orders (id),
    revision_no    INT          NOT NULL,
    accepted_by    VARCHAR(120) NOT NULL,
    channel        VARCHAR(12)  NOT NULL CHECK (channel IN ('EMAIL','PHONE','SIGNED_QUOTE','CUSTOMER_PO','PORTAL','OTHER')),
    reference      VARCHAR(80),
    accepted_on    DATE         NOT NULL,
    recorded_by    UUID,
    recorded_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_fuel_order_acceptance ON fuel_order_acceptances (order_id);

-- Existing quotations get a revision 1 from their current terms, so history is never empty for them.
INSERT INTO fuel_order_revisions (id, tenant_id, order_id, revision_no, site_id, fuel_type, litres, price_per_litre, requested_date, quote_valid_until, customer_reference, notes, recorded_by, recorded_at)
SELECT gen_random_uuid(), o.tenant_id, o.id, 1, o.site_id, o.fuel_type, o.litres_ordered, o.price_per_litre, o.requested_date, o.quote_valid_until, o.customer_reference, o.notes, o.created_by, o.created_at
  FROM fuel_sales_orders o
 WHERE NOT EXISTS (SELECT 1 FROM fuel_order_revisions r WHERE r.order_id = o.id);
