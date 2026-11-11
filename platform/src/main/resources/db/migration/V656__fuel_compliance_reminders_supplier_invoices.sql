-- Fuel: compliance expiry reminders (one row per item, expiry date and stage so nothing is emailed twice)
-- and supplier invoices matched against purchase orders and receipts.

CREATE TABLE fuel_compliance_reminders (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL,
    item_id     UUID        NOT NULL REFERENCES fuel_compliance_items (id),
    expires_on  DATE        NOT NULL,
    stage       VARCHAR(10) NOT NULL,
    sent_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_fuel_reminder_stage CHECK (stage IN ('D30','D7','EXPIRED')),
    CONSTRAINT uq_fuel_reminder UNIQUE (item_id, expires_on, stage)
);

CREATE TABLE fuel_supplier_invoices (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID          NOT NULL,
    supplier_id         UUID          NOT NULL REFERENCES fuel_suppliers (id),
    purchase_order_id   UUID          REFERENCES fuel_purchase_orders (id),
    invoice_number      VARCHAR(60)   NOT NULL,
    invoice_date        DATE          NOT NULL,
    due_date            DATE,
    litres              NUMERIC(12,2) NOT NULL,
    price_per_litre     NUMERIC(10,4) NOT NULL,
    other_charges       NUMERIC(12,2) NOT NULL DEFAULT 0,
    vat_amount          NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_amount        NUMERIC(14,2) NOT NULL,
    status              VARCHAR(10)   NOT NULL DEFAULT 'CAPTURED',
    match_status        VARCHAR(10)   NOT NULL DEFAULT 'UNMATCHED',
    decision_reason     VARCHAR(500),
    notes               VARCHAR(500),
    captured_by         UUID,
    captured_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    decided_by          UUID,
    decided_at          TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_fuel_sinv_status CHECK (status IN ('CAPTURED','APPROVED','REJECTED')),
    CONSTRAINT chk_fuel_sinv_match CHECK (match_status IN ('MATCHED','VARIANCE','UNMATCHED')),
    CONSTRAINT chk_fuel_sinv_amounts CHECK (litres > 0 AND price_per_litre >= 0 AND other_charges >= 0 AND vat_amount >= 0 AND total_amount >= 0)
);
-- The same supplier invoice number cannot be live twice; a rejected one can be captured again.
CREATE UNIQUE INDEX uq_fuel_sinv_number ON fuel_supplier_invoices (tenant_id, supplier_id, upper(invoice_number)) WHERE status <> 'REJECTED';
CREATE INDEX ix_fuel_sinv_tenant ON fuel_supplier_invoices (tenant_id, captured_at DESC);

CREATE TABLE fuel_supplier_invoice_receipts (
    id          UUID PRIMARY KEY,
    invoice_id  UUID NOT NULL REFERENCES fuel_supplier_invoices (id),
    receipt_id  UUID NOT NULL REFERENCES fuel_receipts (id),
    tenant_id   UUID NOT NULL,
    CONSTRAINT uq_fuel_sinv_link UNIQUE (invoice_id, receipt_id)
);
CREATE INDEX ix_fuel_sinv_links_invoice ON fuel_supplier_invoice_receipts (tenant_id, invoice_id);
-- A receipt is on one invoice at a time. Rejecting an invoice deletes its links, which frees the receipts.
CREATE UNIQUE INDEX uq_fuel_sinv_receipt ON fuel_supplier_invoice_receipts (receipt_id);
