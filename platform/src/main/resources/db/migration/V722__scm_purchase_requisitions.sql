-- Supply Chain: purchase requisitions (request -> approve -> convert to PO / RFQ).
--
-- V86 created sc_purchase_requisitions and sc_requisition_lines, but nothing ever used them (no entity or query
-- references them) and their shape differs from the new model. The old lines table is renamed, not dropped, so any
-- data survives; the new model uses sc_requisitions and a new sc_requisition_lines.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'sc_requisition_lines')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_name = 'sc_requisition_lines' AND column_name = 'line_number') THEN
        ALTER TABLE sc_requisition_lines RENAME TO sc_requisition_lines_legacy;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS sc_requisitions (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID          NOT NULL,
    requisition_number  VARCHAR(20)   NOT NULL,
    requisition_type    VARCHAR(30)   NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    title               VARCHAR(200)  NOT NULL,
    business_reason     VARCHAR(1000) NOT NULL,
    department          VARCHAR(100),
    cost_centre         VARCHAR(100),
    project_ref         VARCHAR(100),
    deliver_to_location UUID,
    required_by_date    DATE,
    currency            VARCHAR(3)    NOT NULL,
    estimated_total     NUMERIC(15,2) NOT NULL,
    requested_by        UUID          NOT NULL,
    requested_by_name   VARCHAR(255),
    submitted_at        TIMESTAMPTZ,
    approved_by         UUID,
    approved_by_name    VARCHAR(255),
    approved_at         TIMESTAMPTZ,
    decision_note       VARCHAR(500),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_sc_requisition_number UNIQUE (tenant_id, requisition_number),
    CONSTRAINT chk_sc_requisition_status CHECK (status IN ('DRAFT','PENDING_APPROVAL','APPROVED','REJECTED','CANCELLED')),
    CONSTRAINT chk_sc_requisition_type CHECK (requisition_type IN
        ('STOCK_REPLENISHMENT','OPERATIONAL','CAPEX','PROJECT','EMERGENCY','SERVICE','CONTRACT_RENEWAL','RECURRING'))
);
CREATE INDEX IF NOT EXISTS idx_sc_requisitions_tenant_status ON sc_requisitions(tenant_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sc_requisitions_requester ON sc_requisitions(tenant_id, requested_by);

CREATE TABLE IF NOT EXISTS sc_requisition_lines (
    id                         UUID PRIMARY KEY,
    tenant_id                  UUID          NOT NULL,
    requisition_id             UUID          NOT NULL REFERENCES sc_requisitions(id) ON DELETE CASCADE,
    line_number                INT           NOT NULL,
    catalogue_item_id          UUID,
    item_name                  VARCHAR(255)  NOT NULL,
    description                VARCHAR(500),
    qty_requested              NUMERIC(12,3) NOT NULL,
    estimated_unit_cost        NUMERIC(15,2) NOT NULL,
    vat_rate                   NUMERIC(5,2)  NOT NULL,
    suggested_supplier_id      UUID,
    stock_snapshot             NUMERIC(12,3),
    on_order_snapshot          NUMERIC(12,3),
    already_requested_snapshot NUMERIC(12,3),
    CONSTRAINT chk_sc_requisition_line_qty CHECK (qty_requested > 0),
    CONSTRAINT chk_sc_requisition_line_cost CHECK (estimated_unit_cost >= 0)
);
CREATE INDEX IF NOT EXISTS idx_sc_requisition_lines_req ON sc_requisition_lines(tenant_id, requisition_id);
CREATE INDEX IF NOT EXISTS idx_sc_requisition_lines_item ON sc_requisition_lines(tenant_id, catalogue_item_id);

CREATE TABLE IF NOT EXISTS sc_requisition_conversions (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID          NOT NULL,
    requisition_id      UUID          NOT NULL REFERENCES sc_requisitions(id),
    requisition_line_id UUID          NOT NULL REFERENCES sc_requisition_lines(id),
    purchase_order_id   UUID          NOT NULL REFERENCES sc_purchase_orders(id),
    qty                 NUMERIC(12,3) NOT NULL,
    unit_cost           NUMERIC(15,2) NOT NULL,
    converted_by        UUID,
    converted_by_name   VARCHAR(255),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_sc_requisition_conversion_qty CHECK (qty > 0)
);
CREATE INDEX IF NOT EXISTS idx_sc_requisition_conversions_req ON sc_requisition_conversions(tenant_id, requisition_id);
CREATE INDEX IF NOT EXISTS idx_sc_requisition_conversions_po ON sc_requisition_conversions(purchase_order_id);

-- sc_purchase_orders.requisition_id was created by V86 as a foreign key to the unused sc_purchase_requisitions table.
-- A PO raised from a new requisition would violate it. Point it at sc_requisitions (NOT VALID: legacy values, if
-- there are any, are left alone; new and changed rows are checked).
ALTER TABLE sc_purchase_orders DROP CONSTRAINT IF EXISTS sc_purchase_orders_requisition_id_fkey;
ALTER TABLE sc_purchase_orders DROP CONSTRAINT IF EXISTS fk_sc_po_requisition;
ALTER TABLE sc_purchase_orders
    ADD CONSTRAINT fk_sc_po_requisition FOREIGN KEY (requisition_id) REFERENCES sc_requisitions(id) NOT VALID;
