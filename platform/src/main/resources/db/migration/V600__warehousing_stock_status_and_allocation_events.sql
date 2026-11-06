-- V600: Warehousing slice 1 (continued): stock status and an allocation audit trail.
--
-- 1. Stock status. A position is now (tenant, client, item, location, status). Existing rows are AVAILABLE, so nothing
--    changes for current stock. Moving stock into QUARANTINE or DAMAGED is a transfer between two positions posted as two
--    ledger movements, so on-hand per position still equals the sum of its movements. Only AVAILABLE stock can be allocated.
-- 2. whse_allocation_events: append-only record of every reservation change (allocate, deallocate, fulfil). Allocation
--    does not change on-hand stock, so it cannot live in whse_stock_movements without breaking the balance rule.
-- 3. The reconciliation view from V599 is rebuilt to include status.

ALTER TABLE whse_inventory ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE';
ALTER TABLE whse_inventory DROP CONSTRAINT IF EXISTS chk_whse_inventory_status;
ALTER TABLE whse_inventory ADD CONSTRAINT chk_whse_inventory_status CHECK (status IN ('AVAILABLE', 'QUARANTINE', 'DAMAGED', 'BLOCKED'));
ALTER TABLE whse_inventory DROP CONSTRAINT IF EXISTS uq_whse_inventory_position;
ALTER TABLE whse_inventory ADD CONSTRAINT uq_whse_inventory_position UNIQUE (tenant_id, client_id, item_id, location_id, status);
-- only available stock may carry an allocation
ALTER TABLE whse_inventory DROP CONSTRAINT IF EXISTS chk_whse_inventory_alloc_status;
ALTER TABLE whse_inventory ADD CONSTRAINT chk_whse_inventory_alloc_status CHECK (status = 'AVAILABLE' OR qty_allocated = 0);

ALTER TABLE whse_stock_movements ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE';
ALTER TABLE whse_stock_movements DROP CONSTRAINT IF EXISTS chk_whse_movement_status;
ALTER TABLE whse_stock_movements ADD CONSTRAINT chk_whse_movement_status CHECK (status IN ('AVAILABLE', 'QUARANTINE', 'DAMAGED', 'BLOCKED'));

CREATE TABLE IF NOT EXISTS whse_allocation_events (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    client_id          UUID NOT NULL REFERENCES whse_clients (id),
    item_id            UUID NOT NULL REFERENCES whse_items (id),
    location_id        UUID NOT NULL REFERENCES whse_locations (id),
    event_type         VARCHAR(12) NOT NULL,
    qty                NUMERIC(12,3) NOT NULL,
    allocated_before   NUMERIC(12,3) NOT NULL,
    allocated_after    NUMERIC(12,3) NOT NULL,
    reference_type     VARCHAR(30),
    reference_id       UUID,
    reference_number   VARCHAR(100),
    recorded_by_user_id UUID,
    created_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_whse_alloc_event_type CHECK (event_type IN ('ALLOCATE', 'DEALLOCATE', 'FULFIL')),
    CONSTRAINT chk_whse_alloc_event_qty CHECK (qty > 0),
    CONSTRAINT chk_whse_alloc_event_arithmetic CHECK (
        (event_type = 'ALLOCATE' AND allocated_after = allocated_before + qty)
     OR (event_type IN ('DEALLOCATE', 'FULFIL') AND allocated_after = allocated_before - qty))
);
CREATE INDEX IF NOT EXISTS idx_whse_alloc_events_position ON whse_allocation_events (tenant_id, client_id, item_id, location_id);
CREATE INDEX IF NOT EXISTS idx_whse_alloc_events_reference ON whse_allocation_events (tenant_id, reference_type, reference_id);

CREATE OR REPLACE FUNCTION whse_allocation_events_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'whse_allocation_events is append-only';
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_whse_allocation_events_no_change ON whse_allocation_events;
CREATE TRIGGER trg_whse_allocation_events_no_change
    BEFORE UPDATE OR DELETE ON whse_allocation_events
    FOR EACH ROW EXECUTE FUNCTION whse_allocation_events_append_only();

-- 3. reconciliation view, now per status
DROP VIEW IF EXISTS whse_ledger_reconciliation;
CREATE VIEW whse_ledger_reconciliation AS
WITH ledger AS (
    SELECT tenant_id, client_id, item_id, location_id, status, SUM(qty_change) AS total
    FROM whse_stock_movements GROUP BY tenant_id, client_id, item_id, location_id, status)
SELECT i.tenant_id, i.client_id, i.item_id, i.location_id, i.status,
       i.qty_on_hand AS balance, COALESCE(l.total, 0) AS ledger_total, i.qty_on_hand - COALESCE(l.total, 0) AS difference
FROM whse_inventory i
LEFT JOIN ledger l ON l.tenant_id = i.tenant_id AND l.client_id = i.client_id AND l.item_id = i.item_id
                  AND l.location_id = i.location_id AND l.status = i.status
WHERE i.qty_on_hand <> COALESCE(l.total, 0)
UNION ALL
SELECT l.tenant_id, l.client_id, l.item_id, l.location_id, l.status, 0, l.total, -l.total
FROM ledger l
WHERE l.total <> 0
  AND NOT EXISTS (SELECT 1 FROM whse_inventory i
                  WHERE i.tenant_id = l.tenant_id AND i.client_id = l.client_id AND i.item_id = l.item_id
                    AND i.location_id = l.location_id AND i.status = l.status);
