-- Stock items, the stock movements ledger, and purchase orders for the internal facilities module.
CREATE TABLE IF NOT EXISTS facility_stock_items (
    id            UUID PRIMARY KEY,
    tenant_id     UUID           NOT NULL,
    sku           VARCHAR(60)    NOT NULL,
    name          VARCHAR(200)   NOT NULL,
    unit          VARCHAR(20)    NOT NULL,
    location      VARCHAR(100),
    on_hand       NUMERIC(14, 3) NOT NULL DEFAULT 0,
    reorder_level NUMERIC(14, 3) NOT NULL DEFAULT 0,
    reorder_qty   NUMERIC(14, 3) NOT NULL DEFAULT 0,
    unit_cost     NUMERIC(15, 2) NOT NULL DEFAULT 0,
    active        BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ    NOT NULL,
    updated_at    TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_facility_stock_sku UNIQUE (tenant_id, sku),
    CONSTRAINT ck_facility_stock_on_hand CHECK (on_hand >= 0),
    CONSTRAINT ck_facility_stock_levels CHECK (reorder_level >= 0 AND reorder_qty >= 0 AND unit_cost >= 0)
);
CREATE INDEX IF NOT EXISTS idx_facility_stock_items_low ON facility_stock_items (tenant_id) WHERE active AND reorder_level > 0;

CREATE TABLE IF NOT EXISTS facility_stock_movements (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID           NOT NULL,
    item_id             UUID           NOT NULL REFERENCES facility_stock_items (id),
    movement_type       VARCHAR(20)    NOT NULL,
    quantity            NUMERIC(14, 3) NOT NULL,
    unit_cost           NUMERIC(15, 2) NOT NULL,
    balance_after       NUMERIC(14, 3) NOT NULL,
    work_order_id       UUID,
    cost_line_id        UUID,
    purchase_order_id   UUID,
    reference           VARCHAR(300),
    created_by_user_id  UUID,
    created_at          TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ck_facility_stock_movement_type CHECK (movement_type IN ('RECEIPT', 'ISSUE', 'RETURN', 'ADJUSTMENT'))
);
CREATE INDEX IF NOT EXISTS idx_facility_stock_mv_item ON facility_stock_movements (tenant_id, item_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_facility_stock_mv_wo ON facility_stock_movements (tenant_id, work_order_id) WHERE work_order_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS facility_purchase_orders (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID         NOT NULL,
    po_number           VARCHAR(40)  NOT NULL,
    vendor_id           UUID         NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    expected_date       DATE,
    notes               VARCHAR(1000),
    created_by_user_id  UUID,
    approved_by_user_id UUID,
    approved_at         TIMESTAMPTZ,
    closed_reason       VARCHAR(500),
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_facility_po_number UNIQUE (tenant_id, po_number),
    CONSTRAINT ck_facility_po_status CHECK (status IN ('DRAFT', 'APPROVED', 'PARTLY_RECEIVED', 'RECEIVED', 'CANCELLED'))
);
CREATE INDEX IF NOT EXISTS idx_facility_po_status ON facility_purchase_orders (tenant_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS facility_purchase_order_lines (
    id                UUID PRIMARY KEY,
    tenant_id         UUID           NOT NULL,
    purchase_order_id UUID           NOT NULL REFERENCES facility_purchase_orders (id) ON DELETE CASCADE,
    line_no           INT            NOT NULL,
    item_id           UUID           NOT NULL REFERENCES facility_stock_items (id),
    description       VARCHAR(300)   NOT NULL,
    ordered_qty       NUMERIC(14, 3) NOT NULL,
    received_qty      NUMERIC(14, 3) NOT NULL DEFAULT 0,
    unit_cost         NUMERIC(15, 2) NOT NULL,
    CONSTRAINT ck_facility_po_line_qty CHECK (ordered_qty > 0 AND received_qty >= 0 AND received_qty <= ordered_qty)
);
CREATE INDEX IF NOT EXISTS idx_facility_po_lines_po ON facility_purchase_order_lines (purchase_order_id);
CREATE INDEX IF NOT EXISTS idx_facility_po_lines_item ON facility_purchase_order_lines (tenant_id, item_id);
