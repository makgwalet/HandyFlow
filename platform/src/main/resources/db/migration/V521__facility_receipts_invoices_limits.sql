-- Goods-receipt documents: one per delivery booked against a purchase order.
CREATE TABLE IF NOT EXISTS facility_goods_receipts (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID           NOT NULL,
    grn_number          VARCHAR(40)    NOT NULL,
    purchase_order_id   UUID           NOT NULL REFERENCES facility_purchase_orders (id),
    vendor_id           UUID           NOT NULL,
    delivery_note       VARCHAR(300),
    received_on         DATE           NOT NULL,
    total_value         NUMERIC(15, 2) NOT NULL,
    received_by_user_id UUID,
    created_at          TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_facility_grn_number UNIQUE (tenant_id, grn_number)
);
CREATE INDEX IF NOT EXISTS idx_facility_grn_po ON facility_goods_receipts (tenant_id, purchase_order_id);

CREATE TABLE IF NOT EXISTS facility_goods_receipt_lines (
    id          UUID PRIMARY KEY,
    tenant_id   UUID           NOT NULL,
    receipt_id  UUID           NOT NULL REFERENCES facility_goods_receipts (id) ON DELETE CASCADE,
    po_line_id  UUID           NOT NULL,
    item_id     UUID           NOT NULL,
    description VARCHAR(300)   NOT NULL,
    quantity    NUMERIC(14, 3) NOT NULL,
    unit_cost   NUMERIC(15, 2) NOT NULL,
    CONSTRAINT ck_facility_grn_line_qty CHECK (quantity > 0)
);
CREATE INDEX IF NOT EXISTS idx_facility_grn_lines_receipt ON facility_goods_receipt_lines (receipt_id);
CREATE INDEX IF NOT EXISTS idx_facility_grn_lines_po_line ON facility_goods_receipt_lines (tenant_id, po_line_id);

-- Vendor invoices recorded against a purchase order, for matching to what was ordered and received.
CREATE TABLE IF NOT EXISTS facility_vendor_invoices (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID           NOT NULL,
    purchase_order_id   UUID           NOT NULL REFERENCES facility_purchase_orders (id),
    vendor_id           UUID           NOT NULL,
    invoice_number      VARCHAR(80)    NOT NULL,
    invoice_date        DATE           NOT NULL,
    subtotal            NUMERIC(15, 2) NOT NULL,
    vat_amount          NUMERIC(15, 2) NOT NULL DEFAULT 0,
    status              VARCHAR(20)    NOT NULL DEFAULT 'RECORDED',
    recorded_by_user_id UUID,
    decided_by_user_id  UUID,
    decided_at          TIMESTAMPTZ,
    decision_note       VARCHAR(500),
    created_at          TIMESTAMPTZ    NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_facility_vendor_invoice UNIQUE (tenant_id, vendor_id, invoice_number),
    CONSTRAINT ck_facility_vendor_invoice_status CHECK (status IN ('RECORDED', 'APPROVED', 'DISPUTED')),
    CONSTRAINT ck_facility_vendor_invoice_amounts CHECK (subtotal > 0 AND vat_amount >= 0)
);
CREATE INDEX IF NOT EXISTS idx_facility_vendor_invoice_po ON facility_vendor_invoices (tenant_id, purchase_order_id);
CREATE INDEX IF NOT EXISTS idx_facility_vendor_invoice_status ON facility_vendor_invoices (tenant_id, status, created_at DESC);

-- Purchase order approval limits per approver. Admins are never limited.
CREATE TABLE IF NOT EXISTS facility_approval_limits (
    id               UUID PRIMARY KEY,
    tenant_id        UUID           NOT NULL,
    user_id          UUID           NOT NULL,
    max_order_value  NUMERIC(15, 2) NOT NULL,
    set_by_user_id   UUID,
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_facility_approval_limit_user UNIQUE (tenant_id, user_id),
    CONSTRAINT ck_facility_approval_limit_value CHECK (max_order_value >= 0)
);
