-- Supply Chain: V86 already created sc_supplier_invoice_lines with a different shape (qty_invoiced / unit_cost,
-- no line_number or created_at), so V730's CREATE TABLE IF NOT EXISTS silently skipped it and Hibernate schema
-- validation failed ("missing column [created_at]"). Same treatment as sc_requisition_lines in V735: the old
-- table is renamed, not dropped, so any data survives, and the table the entity expects is created in its place.
-- Safe to run when V730 already created the correct table (nothing is renamed or recreated then).

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = current_schema() AND table_name = 'sc_supplier_invoice_lines')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = current_schema() AND table_name = 'sc_supplier_invoice_lines'
                         AND column_name = 'line_number') THEN
        ALTER TABLE sc_supplier_invoice_lines RENAME TO sc_supplier_invoice_lines_legacy;
        -- The indexes V730 created on the legacy table kept their names; free them for the new table.
        DROP INDEX IF EXISTS idx_sc_inv_lines_invoice;
        DROP INDEX IF EXISTS idx_sc_inv_lines_po_line;
    END IF;
END $$;

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
