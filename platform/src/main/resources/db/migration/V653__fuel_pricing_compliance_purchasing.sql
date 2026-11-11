-- Fuel Stage 4: price lists and contract prices, compliance register, supplier purchase orders.

CREATE TABLE IF NOT EXISTS fuel_prices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID          NOT NULL,
    fuel_type       VARCHAR(30)   NOT NULL,
    account_id      UUID          REFERENCES fuel_customer_accounts(id),   -- NULL = standard price list
    price_per_litre NUMERIC(10,4) NOT NULL,
    min_litres      NUMERIC(12,2) NOT NULL DEFAULT 0,                      -- volume break
    valid_from      DATE          NOT NULL,
    valid_to        DATE,
    note            VARCHAR(200),
    created_by      UUID,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    ended_at        TIMESTAMPTZ,
    CONSTRAINT chk_fuel_price_value CHECK (price_per_litre >= 0),
    CONSTRAINT chk_fuel_price_min CHECK (min_litres >= 0),
    CONSTRAINT chk_fuel_price_dates CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
CREATE INDEX IF NOT EXISTS ix_fuel_price_lookup ON fuel_prices (tenant_id, fuel_type, account_id, valid_from);

CREATE TABLE IF NOT EXISTS fuel_compliance_items (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL,
    item_type     VARCHAR(20)  NOT NULL,
    title         VARCHAR(120) NOT NULL,
    reference_no  VARCHAR(80),
    subject_type  VARCHAR(12)  NOT NULL,
    subject_id    UUID,
    subject_label VARCHAR(120),
    issued_on     DATE,
    expires_on    DATE         NOT NULL,
    notes         VARCHAR(500),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_fuel_comp_item_type CHECK (item_type IN ('LICENCE','CALIBRATION','INSPECTION','CERTIFICATE','INSURANCE','OTHER')),
    CONSTRAINT chk_fuel_comp_subject CHECK (subject_type IN ('COMPANY','DEPOT','TANK','TANKER','DRIVER','OTHER')),
    CONSTRAINT chk_fuel_comp_subject_id CHECK (subject_type IN ('COMPANY','OTHER') OR subject_id IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS ix_fuel_comp_expiry ON fuel_compliance_items (tenant_id, expires_on) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_fuel_comp_subject ON fuel_compliance_items (tenant_id, subject_type, subject_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS fuel_compliance_renewals (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL,
    item_id             UUID        NOT NULL REFERENCES fuel_compliance_items(id),
    previous_expires_on DATE        NOT NULL,
    new_expires_on      DATE        NOT NULL,
    renewed_by          UUID,
    renewed_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    note                VARCHAR(200)
);

CREATE TABLE IF NOT EXISTS fuel_po_sequences (
    tenant_id UUID NOT NULL, year INT NOT NULL, last_seq INT NOT NULL DEFAULT 0, PRIMARY KEY (tenant_id, year)
);

CREATE TABLE IF NOT EXISTS fuel_purchase_orders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID          NOT NULL,
    po_number       VARCHAR(20)   NOT NULL,
    supplier_id     UUID          NOT NULL,
    fuel_type       VARCHAR(30)   NOT NULL,
    litres_ordered  NUMERIC(12,2) NOT NULL,
    price_per_litre NUMERIC(10,4) NOT NULL,
    status          VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
    expected_date   DATE,
    cancel_reason   VARCHAR(300),
    notes           VARCHAR(500),
    created_by      UUID,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_fuel_po_litres CHECK (litres_ordered > 0),
    CONSTRAINT chk_fuel_po_price CHECK (price_per_litre >= 0),
    CONSTRAINT chk_fuel_po_status CHECK (status IN ('DRAFT','ORDERED','PART_RECEIVED','RECEIVED','CANCELLED'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_fuel_po_number ON fuel_purchase_orders (tenant_id, po_number);

ALTER TABLE fuel_receipts ADD COLUMN IF NOT EXISTS purchase_order_id UUID REFERENCES fuel_purchase_orders(id);
CREATE INDEX IF NOT EXISTS ix_fuel_receipt_po ON fuel_receipts (tenant_id, purchase_order_id);
