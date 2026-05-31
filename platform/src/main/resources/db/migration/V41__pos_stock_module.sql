-- V41__pos_stock_module.sql
-- POS & Stock — point of sale transactions, inventory management,
-- stock movements, purchase orders, low stock alerts.
-- WHY universal? Kota joints, kiosks, restaurants, retail — all share
-- the same core: select item → add to sale → take payment → stock down.
--
-- Catalogue integration: pos_stock_items references catalogue_items.
-- Price comes from catalogue_items.default_price — no duplication.
-- Accounting: every sale posts journal entries (cash/card DR, sales CR).

-- ── Add barcode/SKU to catalogue_items (missing columns) ─────────────────────
ALTER TABLE catalogue_items
    ADD COLUMN IF NOT EXISTS barcode VARCHAR(100),
    ADD COLUMN IF NOT EXISTS sku     VARCHAR(50);

CREATE INDEX IF NOT EXISTS idx_catalogue_items_barcode
    ON catalogue_items(tenant_id, barcode) WHERE barcode IS NOT NULL;

-- ── Stock items (inventory levels per catalogue item) ─────────────────────────
-- WHY separate from catalogue_items? Not every tenant uses stock tracking.
-- A service business uses catalogue for invoicing but has no physical stock.
-- POS module adds stock tracking on top of the existing catalogue.
CREATE TABLE pos_stock_items (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    catalogue_item_id UUID      NOT NULL REFERENCES catalogue_items(id) ON DELETE CASCADE,
    qty_on_hand     NUMERIC(12,3) NOT NULL DEFAULT 0,
    qty_reserved    NUMERIC(12,3) NOT NULL DEFAULT 0,  -- in pending orders
    reorder_level   NUMERIC(12,3) NOT NULL DEFAULT 0,  -- alert threshold
    reorder_qty     NUMERIC(12,3) NOT NULL DEFAULT 0,  -- suggested order qty
    cost_price      NUMERIC(15,2) NOT NULL DEFAULT 0,  -- average landed cost
    location        VARCHAR(100),   -- shelf/bin location e.g. "Aisle 3, Shelf B"
    track_stock     BOOLEAN     NOT NULL DEFAULT true,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_pos_stock_items PRIMARY KEY (id),
    CONSTRAINT uq_pos_stock_item UNIQUE (tenant_id, catalogue_item_id)
);

CREATE INDEX idx_pos_stock_tenant   ON pos_stock_items(tenant_id);
CREATE INDEX idx_pos_stock_low      ON pos_stock_items(tenant_id, qty_on_hand, reorder_level);

-- ── Stock movements (every in/out — full audit trail) ─────────────────────────
-- WHY audit every movement? Stock accuracy is critical.
-- Discrepancies must be traceable to a specific transaction.
CREATE TABLE pos_stock_movements (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    stock_item_id   UUID        NOT NULL REFERENCES pos_stock_items(id),
    movement_type   VARCHAR(30) NOT NULL
        CHECK (movement_type IN (
            'SALE',           -- stock out from a POS sale
            'RETURN',         -- stock in from a customer return
            'PURCHASE',       -- stock in from a purchase order
            'ADJUSTMENT',     -- manual stock count correction
            'WASTE',          -- spoilage/damage write-off
            'TRANSFER_IN',    -- received from another location
            'TRANSFER_OUT',   -- sent to another location
            'OPENING'         -- opening stock entry
        )),
    qty_change      NUMERIC(12,3) NOT NULL,  -- positive = in, negative = out
    qty_before      NUMERIC(12,3) NOT NULL,
    qty_after       NUMERIC(12,3) NOT NULL,
    reference_type  VARCHAR(30),             -- SALE, PURCHASE_ORDER, ADJUSTMENT
    reference_id    UUID,                    -- ID of the source document
    notes           TEXT,
    created_by      UUID        REFERENCES users(id),
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_pos_stock_movements PRIMARY KEY (id)
);

CREATE INDEX idx_pos_movements_stock  ON pos_stock_movements(stock_item_id);
CREATE INDEX idx_pos_movements_tenant ON pos_stock_movements(tenant_id, created_at DESC);

-- ── POS transactions (sale header) ───────────────────────────────────────────
CREATE TABLE pos_transactions (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    transaction_number VARCHAR(20) NOT NULL,
    -- Customer (optional — walk-in customers have no customer_id)
    customer_id     UUID        REFERENCES customers(id) ON DELETE SET NULL,
    customer_name   VARCHAR(255),   -- for walk-in / one-time customers
    -- Totals
    subtotal        NUMERIC(15,2) NOT NULL DEFAULT 0,
    vat_amount      NUMERIC(15,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_amount    NUMERIC(15,2) NOT NULL DEFAULT 0,
    -- Payment
    payment_method  VARCHAR(20) NOT NULL DEFAULT 'CASH'
        CHECK (payment_method IN ('CASH','CARD','EFT','ACCOUNT','SPLIT','VOUCHER')),
    amount_tendered NUMERIC(15,2),   -- cash tendered by customer
    change_given    NUMERIC(15,2),   -- change returned
    payment_ref     VARCHAR(100),    -- EFT reference / card auth code
    -- Status
    status          VARCHAR(20) NOT NULL DEFAULT 'COMPLETED'
        CHECK (status IN ('DRAFT','COMPLETED','VOIDED','REFUNDED')),
    -- Accounting
    journal_entry_id UUID,
    -- Staff
    served_by       UUID        REFERENCES users(id),
    served_by_name  VARCHAR(255),
    notes           TEXT,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    voided_at       TIMESTAMP,
    voided_reason   TEXT,

    CONSTRAINT pk_pos_transactions PRIMARY KEY (id),
    CONSTRAINT uq_pos_txn_number UNIQUE (tenant_id, transaction_number)
);

CREATE INDEX idx_pos_txn_tenant  ON pos_transactions(tenant_id, created_at DESC);
CREATE INDEX idx_pos_txn_status  ON pos_transactions(tenant_id, status);
CREATE INDEX idx_pos_txn_customer ON pos_transactions(customer_id) WHERE customer_id IS NOT NULL;

-- ── Transaction line items ────────────────────────────────────────────────────
CREATE TABLE pos_transaction_items (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    transaction_id      UUID        NOT NULL REFERENCES pos_transactions(id) ON DELETE CASCADE,
    tenant_id           UUID        NOT NULL,
    catalogue_item_id   UUID        REFERENCES catalogue_items(id) ON DELETE SET NULL,
    item_name           VARCHAR(255) NOT NULL,  -- denormalised for display
    sku                 VARCHAR(50),
    qty                 NUMERIC(12,3) NOT NULL,
    unit_price          NUMERIC(15,2) NOT NULL,
    vat_rate            NUMERIC(5,2)  NOT NULL DEFAULT 15,
    vat_amount          NUMERIC(15,2) NOT NULL DEFAULT 0,
    discount_pct        NUMERIC(5,2)  NOT NULL DEFAULT 0,
    discount_amount     NUMERIC(15,2) NOT NULL DEFAULT 0,
    line_total          NUMERIC(15,2) NOT NULL,

    CONSTRAINT pk_pos_txn_items PRIMARY KEY (id)
);

CREATE INDEX idx_pos_txn_items_txn ON pos_transaction_items(transaction_id);

-- ── Purchase orders (stock replenishment from suppliers) ──────────────────────
CREATE TABLE pos_purchase_orders (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    order_number    VARCHAR(20) NOT NULL,
    supplier_id     UUID        REFERENCES customers(id) ON DELETE SET NULL,
    supplier_name   VARCHAR(255) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','ORDERED','PARTIALLY_RECEIVED','RECEIVED','CANCELLED')),
    order_date      DATE        NOT NULL DEFAULT CURRENT_DATE,
    expected_date   DATE,
    received_date   DATE,
    subtotal        NUMERIC(15,2) NOT NULL DEFAULT 0,
    vat_amount      NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_amount    NUMERIC(15,2) NOT NULL DEFAULT 0,
    notes           TEXT,
    created_by      UUID        REFERENCES users(id),
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_pos_po PRIMARY KEY (id),
    CONSTRAINT uq_pos_po_number UNIQUE (tenant_id, order_number)
);

CREATE INDEX idx_pos_po_tenant ON pos_purchase_orders(tenant_id);
CREATE INDEX idx_pos_po_status ON pos_purchase_orders(tenant_id, status);

-- ── Purchase order line items ─────────────────────────────────────────────────
CREATE TABLE pos_purchase_order_items (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    purchase_order_id   UUID        NOT NULL REFERENCES pos_purchase_orders(id) ON DELETE CASCADE,
    tenant_id           UUID        NOT NULL,
    catalogue_item_id   UUID        REFERENCES catalogue_items(id) ON DELETE SET NULL,
    item_name           VARCHAR(255) NOT NULL,
    qty_ordered         NUMERIC(12,3) NOT NULL,
    qty_received        NUMERIC(12,3) NOT NULL DEFAULT 0,
    unit_cost           NUMERIC(15,2) NOT NULL,
    vat_rate            NUMERIC(5,2)  NOT NULL DEFAULT 15,
    line_total          NUMERIC(15,2) NOT NULL,

    CONSTRAINT pk_pos_po_items PRIMARY KEY (id)
);

CREATE INDEX idx_pos_po_items_po ON pos_purchase_order_items(purchase_order_id);

-- ── Stock adjustments ────────────────────────────────────────────────────────
CREATE TABLE pos_stock_adjustments (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    adjustment_number VARCHAR(20) NOT NULL,
    reason          VARCHAR(30) NOT NULL
        CHECK (reason IN ('STOCK_COUNT','DAMAGE','THEFT','EXPIRY','CORRECTION','OTHER')),
    notes           TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','APPLIED')),
    created_by      UUID        REFERENCES users(id),
    applied_by      UUID        REFERENCES users(id),
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    applied_at      TIMESTAMP,

    CONSTRAINT pk_pos_adjustments PRIMARY KEY (id),
    CONSTRAINT uq_pos_adj_number UNIQUE (tenant_id, adjustment_number)
);

-- ── Adjustment line items ─────────────────────────────────────────────────────
CREATE TABLE pos_adjustment_items (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    adjustment_id   UUID        NOT NULL REFERENCES pos_stock_adjustments(id) ON DELETE CASCADE,
    stock_item_id   UUID        NOT NULL REFERENCES pos_stock_items(id),
    qty_system      NUMERIC(12,3) NOT NULL,   -- what the system thinks
    qty_actual      NUMERIC(12,3) NOT NULL,   -- what was physically counted
    qty_difference  NUMERIC(12,3) NOT NULL,   -- actual - system

    CONSTRAINT pk_pos_adj_items PRIMARY KEY (id)
);

-- ── Module catalogue ──────────────────────────────────────────────────────────
INSERT INTO module_catalogue (key, name, description, monthly_price, icon, category, sort_order)
VALUES (
    'pos',
    'POS & Stock',
    'Point of sale, inventory management, stock movements, purchase orders, low stock alerts. Works for retail, restaurants, kiosks and any product-based business.',
    399.00, 'shopping-cart', 'OPERATIONS', 99
) ON CONFLICT (key) DO NOTHING;

INSERT INTO tenant_modules (tenant_id, module_key, status, trial_ends_at)
SELECT t.id, 'pos', 'TRIAL', NOW() + INTERVAL '60 days'
FROM tenants t WHERE t.slug = 'zeta-earthmoving'
ON CONFLICT (tenant_id, module_key) DO NOTHING;

-- ── Permissions ───────────────────────────────────────────────────────────────
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'POS_READ',    'View sales transactions and stock levels'),
    (gen_random_uuid(), 'POS_SELL',    'Process sales at the POS terminal'),
    (gen_random_uuid(), 'POS_MANAGE',  'Manage stock items, purchase orders and adjustments'),
    (gen_random_uuid(), 'POS_ADMIN',   'Void transactions and manage POS settings')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('POS_READ','POS_SELL','POS_MANAGE','POS_ADMIN')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
