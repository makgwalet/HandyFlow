-- V609: Warehousing lots, part 2: first-expiry-first-out allocation and recall.
--
-- An outbound line for a lot-tracked item is allocated lot by lot, earliest expiry first, skipping blocked lots and lots too
-- close to expiry for the item's minimum shelf life. Each piece of an allocation is recorded here so it can be shipped or released
-- exactly. A lot can be blocked (a recall or a quality hold); blocked stock is never allocated and cannot be shipped.

ALTER TABLE whse_lots
    ADD COLUMN IF NOT EXISTS blocked         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS blocked_reason  VARCHAR(300),
    ADD COLUMN IF NOT EXISTS blocked_at      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS blocked_by      UUID;

ALTER TABLE whse_lots DROP CONSTRAINT IF EXISTS chk_whse_lot_blocked;
ALTER TABLE whse_lots ADD CONSTRAINT chk_whse_lot_blocked CHECK (NOT blocked OR (blocked_reason IS NOT NULL AND length(trim(blocked_reason)) >= 5));

CREATE TABLE whse_order_lot_allocations (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    order_id      UUID NOT NULL REFERENCES whse_outbound_orders (id),
    order_line_id UUID NOT NULL REFERENCES whse_outbound_order_lines (id),
    inventory_id  UUID NOT NULL REFERENCES whse_inventory (id),
    lot_id        UUID NOT NULL REFERENCES whse_lots (id),
    location_id   UUID NOT NULL REFERENCES whse_locations (id),
    qty           NUMERIC(12,3) NOT NULL CHECK (qty > 0),
    status        VARCHAR(10) NOT NULL DEFAULT 'ALLOCATED' CHECK (status IN ('ALLOCATED','SHIPPED','RELEASED')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at     TIMESTAMPTZ
);
CREATE INDEX idx_whse_order_lot_alloc_order ON whse_order_lot_allocations (tenant_id, order_id);
CREATE INDEX idx_whse_order_lot_alloc_open_lot ON whse_order_lot_allocations (lot_id) WHERE status = 'ALLOCATED';
