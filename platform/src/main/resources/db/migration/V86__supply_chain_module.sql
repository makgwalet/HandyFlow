-- V43__supply_chain_module.sql
-- Supply Chain Management — suppliers, purchase orders, goods receipts,
-- inventory, stock movements, delivery orders, supplier invoices.
-- WHY a separate SCM module vs POS stock?
-- POS stock = simple retail (sell items, track qty).
-- SCM = procurement workflow (RFQ → PO → GR → supplier invoice → 3-way match).
-- They share the catalogue_items table but serve different workflows.

-- ── Supplier register ────────────────────────────────────────────────────────
-- WHY separate from customers? Suppliers are vendors you buy from;
-- customers are entities you sell to. They can overlap (a customer who also
-- supplies you) but the commercial relationship and data differ.
CREATE TABLE sc_suppliers (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name                VARCHAR(255) NOT NULL,
    registration_number VARCHAR(50),         -- CIPC company reg
    vat_number          VARCHAR(30),
    -- BBBEE (South African procurement requirement)
    bbbee_level         INT         CHECK (bbbee_level BETWEEN 1 AND 8),
    bbbee_certificate   VARCHAR(500),        -- URL or file ref
    bbbee_expiry        DATE,
    -- Contact
    contact_name        VARCHAR(255),
    contact_email       VARCHAR(255),
    contact_phone       VARCHAR(30),
    website             VARCHAR(500),
    -- Address
    street              VARCHAR(255),
    suburb              VARCHAR(100),
    city                VARCHAR(100),
    province            VARCHAR(50),
    postal_code         VARCHAR(10),
    country             VARCHAR(50) NOT NULL DEFAULT 'South Africa',
    -- Banking (for payment runs)
    bank_name           VARCHAR(100),
    bank_account        VARCHAR(30),
    bank_branch_code    VARCHAR(10),
    -- Procurement
    payment_terms_days  INT         NOT NULL DEFAULT 30,  -- e.g. 30 = net 30
    currency            VARCHAR(3)  NOT NULL DEFAULT 'ZAR',
    -- Performance (denormalised for dashboard speed)
    total_orders        INT         NOT NULL DEFAULT 0,
    on_time_deliveries  INT         NOT NULL DEFAULT 0,
    late_deliveries     INT         NOT NULL DEFAULT 0,
    defect_count        INT         NOT NULL DEFAULT 0,
    -- Status
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE','INACTIVE','BLACKLISTED')),
    -- Preferred supplier per category (JSON array of category names)
    preferred_categories TEXT,
    notes               TEXT,
    created_by          UUID        REFERENCES users(id),
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMP,

    CONSTRAINT pk_sc_suppliers PRIMARY KEY (id)
);

CREATE INDEX idx_sc_suppliers_tenant  ON sc_suppliers(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_sc_suppliers_status  ON sc_suppliers(tenant_id, status) WHERE deleted_at IS NULL;

-- ── Supplier catalogue (what each supplier sells + their price) ──────────────
-- WHY? A supplier may sell the same item as another at a different price.
-- This drives automatic "best price" suggestions on PO creation.
CREATE TABLE sc_supplier_items (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    supplier_id         UUID        NOT NULL REFERENCES sc_suppliers(id) ON DELETE CASCADE,
    tenant_id           UUID        NOT NULL,
    catalogue_item_id   UUID        REFERENCES catalogue_items(id) ON DELETE SET NULL,
    item_name           VARCHAR(255) NOT NULL,   -- supplier's name for the item
    supplier_sku        VARCHAR(100),
    unit_cost           NUMERIC(15,2) NOT NULL,
    currency            VARCHAR(3)  NOT NULL DEFAULT 'ZAR',
    lead_time_days      INT         NOT NULL DEFAULT 7,
    min_order_qty       NUMERIC(12,3) NOT NULL DEFAULT 1,
    is_preferred        BOOLEAN     NOT NULL DEFAULT false,
    last_ordered_at     TIMESTAMP,
    last_ordered_price  NUMERIC(15,2),
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_sc_supplier_items PRIMARY KEY (id)
);

CREATE INDEX idx_sc_supplier_items_supplier  ON sc_supplier_items(supplier_id);
CREATE INDEX idx_sc_supplier_items_catalogue ON sc_supplier_items(catalogue_item_id) WHERE catalogue_item_id IS NOT NULL;

-- ── Stock locations (warehouse, site, vehicle, store) ────────────────────────
-- WHY? A business may have head office stock, site stock, and vehicle stock.
-- Each location tracks independently. Transfers move between locations.
CREATE TABLE sc_stock_locations (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,       -- "Head Office", "Site A", "Truck 3"
    location_type VARCHAR(20) NOT NULL DEFAULT 'WAREHOUSE'
        CHECK (location_type IN ('WAREHOUSE','SITE','VEHICLE','STORE','VIRTUAL')),
    address     VARCHAR(500),
    is_default  BOOLEAN     NOT NULL DEFAULT false,
    active      BOOLEAN     NOT NULL DEFAULT true,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_sc_stock_locations PRIMARY KEY (id),
    CONSTRAINT uq_sc_location_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_sc_stock_locations_tenant ON sc_stock_locations(tenant_id);

-- ── Inventory (qty per item per location) ───────────────────────────────────
-- WHY separate from pos_stock_items? POS stock is for retail terminal use.
-- SCM inventory covers all locations including warehouses and sites that
-- have no POS terminal. A tenant using SCM but not POS still needs inventory.
CREATE TABLE sc_inventory (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    location_id         UUID        NOT NULL REFERENCES sc_stock_locations(id),
    catalogue_item_id   UUID        NOT NULL REFERENCES catalogue_items(id) ON DELETE CASCADE,
    -- Quantities
    qty_on_hand         NUMERIC(12,3) NOT NULL DEFAULT 0,
    qty_reserved        NUMERIC(12,3) NOT NULL DEFAULT 0,    -- in open POs
    qty_in_transit      NUMERIC(12,3) NOT NULL DEFAULT 0,    -- ordered, not received
    -- Reorder
    reorder_point       NUMERIC(12,3) NOT NULL DEFAULT 0,
    reorder_qty         NUMERIC(12,3) NOT NULL DEFAULT 0,
    max_stock_level     NUMERIC(12,3),
    -- Costing (weighted average)
    avg_cost            NUMERIC(15,2) NOT NULL DEFAULT 0,
    last_cost           NUMERIC(15,2) NOT NULL DEFAULT 0,
    -- Tracking
    bin_location        VARCHAR(50),          -- physical bin/shelf location
    expiry_tracking     BOOLEAN     NOT NULL DEFAULT false,
    lot_tracking        BOOLEAN     NOT NULL DEFAULT false,
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_sc_inventory PRIMARY KEY (id),
    CONSTRAINT uq_sc_inventory UNIQUE (tenant_id, location_id, catalogue_item_id)
);

CREATE INDEX idx_sc_inventory_tenant   ON sc_inventory(tenant_id);
CREATE INDEX idx_sc_inventory_location ON sc_inventory(location_id);
CREATE INDEX idx_sc_inventory_item     ON sc_inventory(catalogue_item_id);
CREATE INDEX idx_sc_inventory_low      ON sc_inventory(tenant_id) WHERE qty_on_hand <= reorder_point;

-- ── Stock movements (immutable audit trail) ──────────────────────────────────
-- WHY immutable? Every qty change must be traceable to a source document.
-- Inventory balance = sum of all movements for that item+location.
CREATE TABLE sc_stock_movements (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    inventory_id        UUID        NOT NULL REFERENCES sc_inventory(id),
    movement_type       VARCHAR(30) NOT NULL
        CHECK (movement_type IN (
            'OPENING',        -- initial stock entry
            'PURCHASE',       -- received from supplier (GR)
            'SALE',           -- consumed by POS sale
            'TRANSFER_IN',    -- received from another location
            'TRANSFER_OUT',   -- sent to another location
            'ADJUSTMENT_UP',  -- stock count correction upward
            'ADJUSTMENT_DOWN',-- stock count correction downward
            'WASTE',          -- spoilage, damage, expiry write-off
            'RETURN_IN',      -- returned from customer
            'RETURN_OUT',     -- returned to supplier
            'PRODUCTION_IN',  -- finished goods from manufacturing
            'PRODUCTION_OUT'  -- raw material consumed in production
        )),
    qty_change          NUMERIC(12,3) NOT NULL,  -- positive = in, negative = out
    qty_before          NUMERIC(12,3) NOT NULL,
    qty_after           NUMERIC(12,3) NOT NULL,
    unit_cost           NUMERIC(15,2),
    -- Source document
    reference_type      VARCHAR(30),   -- GOODS_RECEIPT, PURCHASE_ORDER, TRANSFER, ADJUSTMENT
    reference_id        UUID,
    reference_number    VARCHAR(50),
    -- Lot/batch tracking
    lot_number          VARCHAR(50),
    expiry_date         DATE,
    notes               TEXT,
    created_by          UUID        REFERENCES users(id),
    created_by_name     VARCHAR(255),
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_sc_movements PRIMARY KEY (id)
);

CREATE INDEX idx_sc_movements_inventory ON sc_stock_movements(inventory_id);
CREATE INDEX idx_sc_movements_tenant    ON sc_stock_movements(tenant_id, created_at DESC);
CREATE INDEX idx_sc_movements_reference ON sc_stock_movements(reference_type, reference_id);

-- ── Purchase Requisitions ────────────────────────────────────────────────────
-- WHY? Before a PO is raised, any team member can request items.
-- Approval workflow turns requisition into a PO.
CREATE TABLE sc_purchase_requisitions (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    requisition_number  VARCHAR(20) NOT NULL,
    title               VARCHAR(255),
    requested_by        UUID        REFERENCES users(id),
    requested_by_name   VARCHAR(255) NOT NULL,
    location_id         UUID        REFERENCES sc_stock_locations(id),
    project_ref         VARCHAR(100),         -- free-text project reference
    required_by_date    DATE,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','CONVERTED','CANCELLED')),
    approved_by         UUID        REFERENCES users(id),
    approved_by_name    VARCHAR(255),
    approved_at         TIMESTAMP,
    rejection_reason    TEXT,
    notes               TEXT,
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_sc_requisitions PRIMARY KEY (id),
    CONSTRAINT uq_sc_req_number UNIQUE (tenant_id, requisition_number)
);

CREATE INDEX idx_sc_requisitions_tenant ON sc_purchase_requisitions(tenant_id);
CREATE INDEX idx_sc_requisitions_status ON sc_purchase_requisitions(tenant_id, status);

-- ── Requisition line items ───────────────────────────────────────────────────
CREATE TABLE sc_requisition_lines (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    requisition_id      UUID        NOT NULL REFERENCES sc_purchase_requisitions(id) ON DELETE CASCADE,
    tenant_id           UUID        NOT NULL,
    catalogue_item_id   UUID        REFERENCES catalogue_items(id) ON DELETE SET NULL,
    item_name           VARCHAR(255) NOT NULL,
    qty_requested       NUMERIC(12,3) NOT NULL,
    qty_approved        NUMERIC(12,3),
    estimated_unit_cost NUMERIC(15,2),
    preferred_supplier_id UUID      REFERENCES sc_suppliers(id) ON DELETE SET NULL,
    justification       TEXT,

    CONSTRAINT pk_sc_req_lines PRIMARY KEY (id)
);

CREATE INDEX idx_sc_req_lines_req ON sc_requisition_lines(requisition_id);

-- ── Purchase Orders ──────────────────────────────────────────────────────────
-- The core procurement document. Sent to supplier; triggers GR when goods arrive.
CREATE TABLE sc_purchase_orders (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    order_number        VARCHAR(20) NOT NULL,
    requisition_id      UUID        REFERENCES sc_purchase_requisitions(id),
    supplier_id         UUID        NOT NULL REFERENCES sc_suppliers(id),
    supplier_name       VARCHAR(255) NOT NULL,  -- denormalised
    -- Delivery
    deliver_to_location UUID        REFERENCES sc_stock_locations(id),
    deliver_to_address  VARCHAR(500),
    -- Dates
    order_date          DATE        NOT NULL DEFAULT CURRENT_DATE,
    required_by_date    DATE,
    expected_delivery   DATE,
    -- Financials
    currency            VARCHAR(3)  NOT NULL DEFAULT 'ZAR',
    exchange_rate       NUMERIC(10,6) NOT NULL DEFAULT 1.0,
    subtotal            NUMERIC(15,2) NOT NULL DEFAULT 0,
    vat_amount          NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_amount        NUMERIC(15,2) NOT NULL DEFAULT 0,
    -- Approval workflow
    -- value-based: <10K auto, 10K-50K manager, >50K director
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN (
            'DRAFT',
            'PENDING_APPROVAL',     -- submitted, waiting for approval
            'APPROVED',             -- approved, not yet sent to supplier
            'SENT',                 -- emailed to supplier
            'ACKNOWLEDGED',         -- supplier confirmed receipt
            'PARTIALLY_RECEIVED',   -- some goods received
            'FULLY_RECEIVED',       -- all goods received
            'INVOICED',             -- supplier invoice matched
            'CANCELLED'
        )),
    approved_by         UUID        REFERENCES users(id),
    approved_by_name    VARCHAR(255),
    approved_at         TIMESTAMP,
    sent_at             TIMESTAMP,
    rejection_reason    TEXT,
    -- Reference
    supplier_reference  VARCHAR(100),  -- supplier's own order ref
    project_ref         VARCHAR(100),
    terms               TEXT,
    notes               TEXT,
    internal_notes      TEXT,
    created_by          UUID        REFERENCES users(id),
    created_by_name     VARCHAR(255),
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    cancelled_at        TIMESTAMP,

    CONSTRAINT pk_sc_po PRIMARY KEY (id),
    CONSTRAINT uq_sc_po_number UNIQUE (tenant_id, order_number)
);

CREATE INDEX idx_sc_po_tenant   ON sc_purchase_orders(tenant_id);
CREATE INDEX idx_sc_po_status   ON sc_purchase_orders(tenant_id, status);
CREATE INDEX idx_sc_po_supplier ON sc_purchase_orders(supplier_id);

-- ── PO line items ────────────────────────────────────────────────────────────
CREATE TABLE sc_po_lines (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    purchase_order_id   UUID        NOT NULL REFERENCES sc_purchase_orders(id) ON DELETE CASCADE,
    tenant_id           UUID        NOT NULL,
    catalogue_item_id   UUID        REFERENCES catalogue_items(id) ON DELETE SET NULL,
    item_name           VARCHAR(255) NOT NULL,
    supplier_sku        VARCHAR(100),
    qty_ordered         NUMERIC(12,3) NOT NULL,
    qty_received        NUMERIC(12,3) NOT NULL DEFAULT 0,
    qty_invoiced        NUMERIC(12,3) NOT NULL DEFAULT 0,
    unit_cost           NUMERIC(15,2) NOT NULL,
    vat_rate            NUMERIC(5,2)  NOT NULL DEFAULT 15,
    vat_amount          NUMERIC(15,2) NOT NULL DEFAULT 0,
    line_total          NUMERIC(15,2) NOT NULL,     -- excl VAT
    line_total_incl     NUMERIC(15,2) NOT NULL,     -- incl VAT
    -- Delivery tracking
    is_fully_received   BOOLEAN     NOT NULL DEFAULT false,
    expected_delivery   DATE,
    notes               TEXT,

    CONSTRAINT pk_sc_po_lines PRIMARY KEY (id)
);

CREATE INDEX idx_sc_po_lines_po   ON sc_po_lines(purchase_order_id);
CREATE INDEX idx_sc_po_lines_item ON sc_po_lines(catalogue_item_id) WHERE catalogue_item_id IS NOT NULL;

-- ── Goods Receipts ───────────────────────────────────────────────────────────
-- WHY? Goods receipts (GRs) confirm physical delivery of ordered items.
-- They trigger stock movements and are the second leg of 3-way matching.
-- 3-way match: PO + GR + Supplier Invoice = pay.
CREATE TABLE sc_goods_receipts (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    receipt_number      VARCHAR(20) NOT NULL,
    purchase_order_id   UUID        NOT NULL REFERENCES sc_purchase_orders(id),
    supplier_id         UUID        NOT NULL REFERENCES sc_suppliers(id),
    received_to         UUID        NOT NULL REFERENCES sc_stock_locations(id),
    -- Delivery details
    delivery_note_ref   VARCHAR(100),   -- supplier's delivery note number
    received_by         UUID        REFERENCES users(id),
    received_by_name    VARCHAR(255),
    received_date       DATE        NOT NULL DEFAULT CURRENT_DATE,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','POSTED','CANCELLED')),
    posted_at           TIMESTAMP,
    notes               TEXT,
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_sc_gr PRIMARY KEY (id),
    CONSTRAINT uq_sc_gr_number UNIQUE (tenant_id, receipt_number)
);

CREATE INDEX idx_sc_gr_tenant ON sc_goods_receipts(tenant_id);
CREATE INDEX idx_sc_gr_po     ON sc_goods_receipts(purchase_order_id);

-- ── GR line items ────────────────────────────────────────────────────────────
CREATE TABLE sc_gr_lines (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    goods_receipt_id    UUID        NOT NULL REFERENCES sc_goods_receipts(id) ON DELETE CASCADE,
    po_line_id          UUID        NOT NULL REFERENCES sc_po_lines(id),
    tenant_id           UUID        NOT NULL,
    catalogue_item_id   UUID        REFERENCES catalogue_items(id) ON DELETE SET NULL,
    item_name           VARCHAR(255) NOT NULL,
    qty_ordered         NUMERIC(12,3) NOT NULL,  -- from PO line
    qty_received        NUMERIC(12,3) NOT NULL,  -- actually received today
    qty_rejected        NUMERIC(12,3) NOT NULL DEFAULT 0,
    rejection_reason    VARCHAR(255),
    unit_cost           NUMERIC(15,2) NOT NULL,
    -- Lot/batch/expiry tracking
    lot_number          VARCHAR(50),
    expiry_date         DATE,
    serial_numbers      TEXT,           -- JSON array of serial numbers
    condition           VARCHAR(20) NOT NULL DEFAULT 'GOOD'
        CHECK (condition IN ('GOOD','PARTIAL','DAMAGED')),

    CONSTRAINT pk_sc_gr_lines PRIMARY KEY (id)
);

CREATE INDEX idx_sc_gr_lines_gr ON sc_gr_lines(goods_receipt_id);

-- ── Supplier Invoices ────────────────────────────────────────────────────────
-- WHY? Separate from tenant invoices (which are for customers).
-- Supplier invoices are AP (accounts payable) documents.
-- 3-way match: PO ↔ GR ↔ Supplier Invoice → approve payment.
CREATE TABLE sc_supplier_invoices (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    invoice_number      VARCHAR(50) NOT NULL,   -- internal ref
    supplier_invoice_ref VARCHAR(100),          -- supplier's own invoice number
    supplier_id         UUID        NOT NULL REFERENCES sc_suppliers(id),
    purchase_order_id   UUID        REFERENCES sc_purchase_orders(id),
    goods_receipt_id    UUID        REFERENCES sc_goods_receipts(id),
    -- Dates
    invoice_date        DATE        NOT NULL,
    due_date            DATE        NOT NULL,
    received_date       DATE        NOT NULL DEFAULT CURRENT_DATE,
    -- Financials
    currency            VARCHAR(3)  NOT NULL DEFAULT 'ZAR',
    subtotal            NUMERIC(15,2) NOT NULL,
    vat_amount          NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_amount        NUMERIC(15,2) NOT NULL,
    -- 3-way match
    match_status        VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (match_status IN (
            'PENDING',      -- not yet matched
            'MATCHED',      -- PO + GR + invoice all match ✓
            'PARTIAL_MATCH',-- some lines match
            'DISPUTE',      -- discrepancy found, under review
            'OVERRIDDEN'    -- manually approved despite mismatch
        )),
    match_notes         TEXT,
    -- Approval
    status              VARCHAR(20) NOT NULL DEFAULT 'RECEIVED'
        CHECK (status IN (
            'RECEIVED',
            'UNDER_REVIEW',
            'APPROVED',
            'DISPUTED',
            'PAID',
            'CANCELLED'
        )),
    approved_by         UUID        REFERENCES users(id),
    approved_by_name    VARCHAR(255),
    approved_at         TIMESTAMP,
    paid_at             TIMESTAMP,
    payment_reference   VARCHAR(100),
    -- Accounting
    journal_entry_id    UUID,   -- posted to acc_journal_entries on approval
    notes               TEXT,
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_sc_supplier_invoices PRIMARY KEY (id),
    CONSTRAINT uq_sc_supplier_invoice UNIQUE (tenant_id, invoice_number)
);

CREATE INDEX idx_sc_invoices_tenant   ON sc_supplier_invoices(tenant_id);
CREATE INDEX idx_sc_invoices_supplier ON sc_supplier_invoices(supplier_id);
CREATE INDEX idx_sc_invoices_status   ON sc_supplier_invoices(tenant_id, status);
CREATE INDEX idx_sc_invoices_due      ON sc_supplier_invoices(due_date) WHERE status NOT IN ('PAID','CANCELLED');

-- ── Supplier Invoice line items ──────────────────────────────────────────────
CREATE TABLE sc_supplier_invoice_lines (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    supplier_invoice_id UUID        NOT NULL REFERENCES sc_supplier_invoices(id) ON DELETE CASCADE,
    po_line_id          UUID        REFERENCES sc_po_lines(id),
    tenant_id           UUID        NOT NULL,
    catalogue_item_id   UUID        REFERENCES catalogue_items(id) ON DELETE SET NULL,
    item_name           VARCHAR(255) NOT NULL,
    qty_invoiced        NUMERIC(12,3) NOT NULL,
    unit_cost           NUMERIC(15,2) NOT NULL,
    vat_rate            NUMERIC(5,2)  NOT NULL DEFAULT 15,
    vat_amount          NUMERIC(15,2) NOT NULL DEFAULT 0,
    line_total          NUMERIC(15,2) NOT NULL,
    -- Match result per line
    match_status        VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (match_status IN ('PENDING','MATCHED','VARIANCE','OVERRIDDEN')),
    variance_reason     TEXT,

    CONSTRAINT pk_sc_invoice_lines PRIMARY KEY (id)
);

CREATE INDEX idx_sc_invoice_lines_inv ON sc_supplier_invoice_lines(supplier_invoice_id);

-- ── Stock Transfers ──────────────────────────────────────────────────────────
-- Move inventory between locations (head office → site, site → vehicle, etc.)
CREATE TABLE sc_stock_transfers (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    transfer_number     VARCHAR(20) NOT NULL,
    from_location       UUID        NOT NULL REFERENCES sc_stock_locations(id),
    to_location         UUID        NOT NULL REFERENCES sc_stock_locations(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','IN_TRANSIT','COMPLETED','CANCELLED')),
    transfer_date       DATE        NOT NULL DEFAULT CURRENT_DATE,
    completed_date      DATE,
    notes               TEXT,
    created_by          UUID        REFERENCES users(id),
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_sc_transfers PRIMARY KEY (id),
    CONSTRAINT uq_sc_transfer_number UNIQUE (tenant_id, transfer_number),
    CONSTRAINT chk_sc_transfer_locations CHECK (from_location <> to_location)
);

CREATE INDEX idx_sc_transfers_tenant ON sc_stock_transfers(tenant_id);

-- ── Transfer line items ──────────────────────────────────────────────────────
CREATE TABLE sc_transfer_lines (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    transfer_id         UUID        NOT NULL REFERENCES sc_stock_transfers(id) ON DELETE CASCADE,
    inventory_id        UUID        NOT NULL REFERENCES sc_inventory(id),
    tenant_id           UUID        NOT NULL,
    catalogue_item_id   UUID        REFERENCES catalogue_items(id),
    item_name           VARCHAR(255) NOT NULL,
    qty_requested       NUMERIC(12,3) NOT NULL,
    qty_transferred     NUMERIC(12,3) NOT NULL DEFAULT 0,
    lot_number          VARCHAR(50),
    notes               TEXT,

    CONSTRAINT pk_sc_transfer_lines PRIMARY KEY (id)
);

CREATE INDEX idx_sc_transfer_lines ON sc_transfer_lines(transfer_id);

-- ── Stock Counts (physical stocktake) ───────────────────────────────────────
-- WHY? Periodic stocktakes reconcile system qty vs physical qty.
-- Variance lines generate adjustment movements.
CREATE TABLE sc_stock_counts (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    count_number        VARCHAR(20) NOT NULL,
    location_id         UUID        NOT NULL REFERENCES sc_stock_locations(id),
    count_date          DATE        NOT NULL DEFAULT CURRENT_DATE,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','IN_PROGRESS','COMPLETED','POSTED')),
    counted_by          UUID        REFERENCES users(id),
    counted_by_name     VARCHAR(255),
    posted_at           TIMESTAMP,
    total_variance_value NUMERIC(15,2),
    notes               TEXT,
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_sc_stock_counts PRIMARY KEY (id),
    CONSTRAINT uq_sc_count_number UNIQUE (tenant_id, count_number)
);

CREATE INDEX idx_sc_counts_tenant ON sc_stock_counts(tenant_id);

-- ── Stock count lines ────────────────────────────────────────────────────────
CREATE TABLE sc_stock_count_lines (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    stock_count_id      UUID        NOT NULL REFERENCES sc_stock_counts(id) ON DELETE CASCADE,
    inventory_id        UUID        NOT NULL REFERENCES sc_inventory(id),
    tenant_id           UUID        NOT NULL,
    catalogue_item_id   UUID        REFERENCES catalogue_items(id),
    item_name           VARCHAR(255) NOT NULL,
    qty_system          NUMERIC(12,3) NOT NULL,   -- system qty at count time
    qty_counted         NUMERIC(12,3),            -- physically counted
    qty_variance        NUMERIC(12,3),            -- counted - system
    unit_cost           NUMERIC(15,2),
    variance_value      NUMERIC(15,2),
    variance_reason     VARCHAR(255),

    CONSTRAINT pk_sc_count_lines PRIMARY KEY (id)
);

CREATE INDEX idx_sc_count_lines ON sc_stock_count_lines(stock_count_id);

-- ── Module catalogue entry ───────────────────────────────────────────────────
INSERT INTO module_catalogue (key, name, description, monthly_price, icon, category, sort_order)
VALUES (
    'supply_chain',
    'Supply Chain',
    'Supplier management with BBBEE scoring, purchase orders with approval workflows, goods receipts, three-way matching, inventory across multiple locations, and stock transfers.',
    599.00, 'truck', 'OPERATIONS', 100
) ON CONFLICT (key) DO NOTHING;

INSERT INTO tenant_modules (tenant_id, module_key, status, trial_ends_at)
SELECT t.id, 'supply_chain', 'TRIAL', NOW() + INTERVAL '60 days'
FROM tenants t WHERE t.slug = 'zeta-earthmoving'
ON CONFLICT (tenant_id, module_key) DO NOTHING;

-- ── Permissions ──────────────────────────────────────────────────────────────
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'SCM_READ',        'View suppliers, POs, inventory and reports'),
    (gen_random_uuid(), 'SCM_REQUISITION', 'Create and submit purchase requisitions'),
    (gen_random_uuid(), 'SCM_ORDER',       'Create and manage purchase orders'),
    (gen_random_uuid(), 'SCM_RECEIVE',     'Create goods receipts and process deliveries'),
    (gen_random_uuid(), 'SCM_INVOICE',     'Process and approve supplier invoices'),
    (gen_random_uuid(), 'SCM_INVENTORY',   'Manage stock levels, transfers and adjustments'),
    (gen_random_uuid(), 'SCM_ADMIN',       'Full supply chain admin including supplier management')
ON CONFLICT (name) DO NOTHING;

-- Grant all SCM permissions to ADMIN role
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('SCM_READ','SCM_REQUISITION','SCM_ORDER','SCM_RECEIVE',
                 'SCM_INVOICE','SCM_INVENTORY','SCM_ADMIN')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
