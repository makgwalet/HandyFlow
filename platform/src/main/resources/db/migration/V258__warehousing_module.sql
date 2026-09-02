-- ============================================================================
-- 3PL / Public Warehousing (outsourced-provider) module — baseline schema + seed.
--
-- *** VERSION NUMBER NOT CONFIRMED — READ BEFORE APPLYING ***
-- V258 follows directly from V257 (collectionsagency, this same engagement,
-- itself unconfirmed). Renumber together with V255/V256/V257 if the real
-- next free version differs.
--
-- *** MODULE CATALOGUE / PERMISSION SEED SHAPE — SAME CAVEAT AS V255-V257 ***
-- Module key "warehousing" (lowercase, no separator), permissions
-- WAREHOUSING_READ/_MANAGE/_ADMIN — same confirmed naming convention.
--
-- *** SCOPE NOTE — see warehousing package-info.java for the full rationale ***
-- This is a NEW, SEPARATE module for a 3PL/public-warehousing operator
-- storing and billing for OTHER businesses' goods. It does not touch, read
-- from, or write to `supplychain`'s tables (sc_stock_locations,
-- sc_inventory, sc_stock_movements, sc_purchase_orders, ...), which model a
-- tenant's OWN internal multi-location inventory. Confirmed as a deliberate,
-- user-approved scope decision before this migration was written.
-- ============================================================================

-- ── Operator profile (one per tenant) ───────────────────────────────────────

CREATE TABLE whse_profile (
    id                                          UUID PRIMARY KEY,
    tenant_id                                   UUID NOT NULL UNIQUE,
    warehouse_name                              VARCHAR(255) NOT NULL,
    registration_number                         VARCHAR(100),
    default_storage_rate_per_unit_per_month     NUMERIC(12,4),
    default_receiving_fee_per_unit              NUMERIC(12,4),
    default_pick_fee_per_unit                   NUMERIC(12,4),
    default_pack_fee_per_order                  NUMERIC(12,2),
    contact_email                               VARCHAR(255),
    contact_phone                               VARCHAR(50),
    physical_address                            TEXT,
    created_at                                  TIMESTAMPTZ NOT NULL,
    updated_at                                  TIMESTAMPTZ NOT NULL
);

-- ── Client portfolio (whose goods this operator stores/fulfils) ────────────

CREATE TABLE whse_clients (
    id                              UUID PRIMARY KEY,
    tenant_id                       UUID NOT NULL,
    trading_name                    VARCHAR(255) NOT NULL,
    registration_number             VARCHAR(100),
    storage_rate_per_unit_per_month NUMERIC(12,4),
    receiving_fee_per_unit          NUMERIC(12,4),
    pick_fee_per_unit               NUMERIC(12,4),
    pack_fee_per_order              NUMERIC(12,2),
    contact_name                    VARCHAR(255),
    contact_email                   VARCHAR(255),
    contact_phone                   VARCHAR(50),
    address                         TEXT,
    onboarded_at                    DATE,
    status                          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes                           TEXT,
    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL,
    deleted_at                      TIMESTAMPTZ,
    version                         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_whse_clients_tenant ON whse_clients (tenant_id) WHERE deleted_at IS NULL;

-- ── Operator's own warehouse locations/bins (not per-client) ───────────────

CREATE TABLE whse_locations (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    code            VARCHAR(50) NOT NULL,
    zone            VARCHAR(100),
    description     VARCHAR(255),
    capacity_units  NUMERIC(12,3),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT uq_whse_location_code UNIQUE (tenant_id, code)
);

CREATE INDEX idx_whse_locations_tenant ON whse_locations (tenant_id) WHERE deleted_at IS NULL;

-- ── Per-client item/SKU catalogue ───────────────────────────────────────────

CREATE TABLE whse_items (
    id                              UUID PRIMARY KEY,
    tenant_id                       UUID NOT NULL,
    client_id                       UUID NOT NULL REFERENCES whse_clients (id),
    sku                             VARCHAR(100) NOT NULL,
    description                     VARCHAR(255) NOT NULL,
    uom                             VARCHAR(20) NOT NULL DEFAULT 'EACH',
    storage_rate_per_unit_per_month NUMERIC(12,4),
    active                          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL,
    deleted_at                      TIMESTAMPTZ,
    CONSTRAINT uq_whse_item_sku UNIQUE (tenant_id, client_id, sku)
);

CREATE INDEX idx_whse_items_client ON whse_items (tenant_id, client_id) WHERE deleted_at IS NULL;

-- ── Live stock position (client x item x location) ──────────────────────────

CREATE TABLE whse_inventory (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL REFERENCES whse_clients (id),
    item_id         UUID NOT NULL REFERENCES whse_items (id),
    location_id     UUID NOT NULL REFERENCES whse_locations (id),
    qty_on_hand     NUMERIC(12,3) NOT NULL DEFAULT 0,
    qty_allocated   NUMERIC(12,3) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_whse_inventory_position UNIQUE (tenant_id, client_id, item_id, location_id),
    CONSTRAINT chk_whse_inventory_nonneg CHECK (qty_on_hand >= 0 AND qty_allocated >= 0 AND qty_allocated <= qty_on_hand)
);

CREATE INDEX idx_whse_inventory_client_item ON whse_inventory (tenant_id, client_id, item_id);

-- ── Inbound shipments (ASNs) + lines ────────────────────────────────────────

CREATE TABLE whse_inbound_shipments (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    client_id           UUID NOT NULL REFERENCES whse_clients (id),
    reference_number    VARCHAR(100),
    expected_date       DATE,
    received_date       DATE,
    status              VARCHAR(20) NOT NULL DEFAULT 'EXPECTED',
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_whse_inbound_client ON whse_inbound_shipments (tenant_id, client_id);
CREATE INDEX idx_whse_inbound_status_expected ON whse_inbound_shipments (status, expected_date);

CREATE TABLE whse_inbound_shipment_lines (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    shipment_id     UUID NOT NULL REFERENCES whse_inbound_shipments (id),
    item_id         UUID NOT NULL REFERENCES whse_items (id),
    expected_qty    NUMERIC(12,3) NOT NULL,
    received_qty    NUMERIC(12,3) NOT NULL DEFAULT 0,
    location_id     UUID REFERENCES whse_locations (id),
    notes           VARCHAR(255)
);

CREATE INDEX idx_whse_inbound_lines_shipment ON whse_inbound_shipment_lines (tenant_id, shipment_id);

-- ── Outbound orders + lines ──────────────────────────────────────────────────

CREATE TABLE whse_outbound_orders (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    client_id               UUID NOT NULL REFERENCES whse_clients (id),
    order_reference         VARCHAR(100),
    ship_to_name            VARCHAR(255),
    ship_to_address         TEXT,
    requested_ship_date     DATE,
    shipped_date            DATE,
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    carrier                 VARCHAR(100),
    tracking_number         VARCHAR(100),
    notes                   TEXT,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_whse_outbound_client ON whse_outbound_orders (tenant_id, client_id);
CREATE INDEX idx_whse_outbound_status_ship_date ON whse_outbound_orders (status, requested_ship_date);

CREATE TABLE whse_outbound_order_lines (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    order_id        UUID NOT NULL REFERENCES whse_outbound_orders (id),
    item_id         UUID NOT NULL REFERENCES whse_items (id),
    location_id     UUID REFERENCES whse_locations (id),
    qty_ordered     NUMERIC(12,3) NOT NULL,
    qty_picked      NUMERIC(12,3) NOT NULL DEFAULT 0,
    notes           VARCHAR(255)
);

CREATE INDEX idx_whse_outbound_lines_order ON whse_outbound_order_lines (tenant_id, order_id);

-- ── Stock movements (append-only audit ledger) ──────────────────────────────

CREATE TABLE whse_stock_movements (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    client_id               UUID NOT NULL REFERENCES whse_clients (id),
    item_id                 UUID NOT NULL REFERENCES whse_items (id),
    location_id             UUID NOT NULL REFERENCES whse_locations (id),
    movement_type           VARCHAR(15) NOT NULL,
    qty_change              NUMERIC(12,3) NOT NULL,
    qty_before              NUMERIC(12,3) NOT NULL,
    qty_after               NUMERIC(12,3) NOT NULL,
    reference_type          VARCHAR(30),
    reference_id            UUID,
    reference_number        VARCHAR(100),
    notes                   TEXT,
    recorded_by_user_id     UUID,
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_whse_movement_type CHECK (movement_type IN ('RECEIPT', 'PICK', 'ADJUSTMENT')),
    CONSTRAINT chk_whse_movement_qty_nonzero CHECK (qty_change <> 0)
);

CREATE INDEX idx_whse_movements_position ON whse_stock_movements (tenant_id, client_id, item_id, location_id);
CREATE INDEX idx_whse_movements_reference ON whse_stock_movements (tenant_id, reference_type, reference_id);

-- ── Billing invoices (the only thing that posts to the real GL) ────────────

CREATE TABLE whse_billing_invoices (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    client_id           UUID NOT NULL REFERENCES whse_clients (id),
    invoice_number      VARCHAR(30) NOT NULL,
    period_start        DATE NOT NULL,
    period_end          DATE NOT NULL,
    invoice_date        DATE NOT NULL,
    due_date            DATE NOT NULL,
    storage_fee         NUMERIC(15,2) NOT NULL,
    handling_fee        NUMERIC(15,2) NOT NULL,
    vat_amount          NUMERIC(15,2) NOT NULL,
    subtotal            NUMERIC(15,2) NOT NULL,
    total               NUMERIC(15,2) NOT NULL,
    amount_paid         NUMERIC(15,2) NOT NULL DEFAULT 0,
    status              VARCHAR(15) NOT NULL DEFAULT 'DRAFT',
    sent_at             TIMESTAMPTZ,
    paid_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_whse_invoice_number UNIQUE (tenant_id, invoice_number)
);

CREATE INDEX idx_whse_invoices_client ON whse_billing_invoices (tenant_id, client_id);
CREATE INDEX idx_whse_invoices_status_due ON whse_billing_invoices (status, due_date);

-- ── Client portal access grants (own table — not shared) ───────────────────

CREATE TABLE whse_portal_access_grants (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    client_id                   UUID NOT NULL REFERENCES whse_clients (id),
    portal_user_id              UUID,
    invite_email                VARCHAR(255) NOT NULL,
    status                      VARCHAR(15) NOT NULL DEFAULT 'PENDING',
    invite_token                VARCHAR(100) UNIQUE,
    invite_token_expires_at     TIMESTAMPTZ,
    invited_by                  UUID,
    invited_at                  TIMESTAMPTZ NOT NULL,
    accepted_at                 TIMESTAMPTZ,
    revoked_by                  UUID,
    revoked_at                  TIMESTAMPTZ
);

CREATE INDEX idx_whse_portal_grants_client ON whse_portal_access_grants (tenant_id, client_id);
CREATE INDEX idx_whse_portal_grants_user ON whse_portal_access_grants (portal_user_id) WHERE status = 'ACTIVE';

-- ── Module catalogue + permission seed ──────────────────────────────────────

INSERT INTO module_catalogue (key, name, description, monthly_price, icon, category, sort_order, is_active)
VALUES ('warehousing', '3PL Warehousing',
        'Third-party / public warehousing operator management — client onboarding, location and item catalogues, inbound receiving, outbound pick-pack-ship, stock allocation, and storage + handling billing.',
        0, 'Warehouse', 'OPERATIONS', 999, true)
ON CONFLICT (key) DO NOTHING;

INSERT INTO permissions (id, name, description)
VALUES
    (gen_random_uuid(), 'WAREHOUSING_READ',   'View warehousing data'),
    (gen_random_uuid(), 'WAREHOUSING_MANAGE', 'Create and manage warehousing records'),
    (gen_random_uuid(), 'WAREHOUSING_ADMIN',  'Full administrative access to warehousing, including issuing billing invoices')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('WAREHOUSING_READ', 'WAREHOUSING_MANAGE', 'WAREHOUSING_ADMIN')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- NOTE — monthly_price left at 0, same open-pricing-track caveat as V255-V257.
