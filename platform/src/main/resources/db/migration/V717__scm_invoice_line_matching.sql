-- Renumber to your next Flyway version.
-- PRE-CHECK before running (the unique index below fails if duplicates already exist):
--   SELECT tenant_id, supplier_id, lower(btrim(supplier_invoice_ref)), count(*)
--   FROM sc_supplier_invoices
--   WHERE supplier_invoice_ref IS NOT NULL AND btrim(supplier_invoice_ref) <> '' AND status <> 'CANCELLED'
--   GROUP BY 1,2,3 HAVING count(*) > 1;

ALTER TABLE sc_supplier_invoices ADD COLUMN IF NOT EXISTS captured_by UUID;

CREATE TABLE IF NOT EXISTS sc_supplier_invoice_lines (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID           NOT NULL,
    supplier_invoice_id UUID           NOT NULL REFERENCES sc_supplier_invoices(id) ON DELETE CASCADE,
    line_number         INT            NOT NULL,
    po_line_id          UUID,
    item_name           VARCHAR(255)   NOT NULL,
    quantity            NUMERIC(12,3)  NOT NULL,
    unit_price          NUMERIC(15,2)  NOT NULL,
    vat_rate            NUMERIC(5,2)   NOT NULL,
    line_total          NUMERIC(15,2)  NOT NULL,
    vat_amount          NUMERIC(15,2)  NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_sc_inv_lines_invoice ON sc_supplier_invoice_lines(tenant_id, supplier_invoice_id);
CREATE INDEX IF NOT EXISTS idx_sc_inv_lines_po_line ON sc_supplier_invoice_lines(po_line_id);

CREATE TABLE IF NOT EXISTS sc_match_exceptions (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID           NOT NULL,
    supplier_invoice_id UUID           NOT NULL REFERENCES sc_supplier_invoices(id) ON DELETE CASCADE,
    po_line_id          UUID,
    invoice_line_id     UUID,
    exception_type      VARCHAR(40)    NOT NULL,
    message             VARCHAR(1000)  NOT NULL,
    expected_value      NUMERIC(18,4),
    actual_value        NUMERIC(18,4),
    status              VARCHAR(12)    NOT NULL DEFAULT 'OPEN',
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    resolved_at         TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_sc_match_exc_invoice ON sc_match_exceptions(tenant_id, supplier_invoice_id, status);

-- Hard duplicate guard (race-safe): same supplier + same supplier invoice ref, ignoring case/whitespace.
CREATE UNIQUE INDEX IF NOT EXISTS uq_sc_invoice_supplier_ref
    ON sc_supplier_invoices (tenant_id, supplier_id, lower(btrim(supplier_invoice_ref)))
    WHERE supplier_invoice_ref IS NOT NULL AND btrim(supplier_invoice_ref) <> '' AND status <> 'CANCELLED';