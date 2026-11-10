-- V608: Warehousing batch / lot tracking and expiry, foundation.
--
-- A lot is a client's batch of one item with a lot number and, for items that expire, an expiry date. For a lot-tracked item the lot
-- becomes part of the stock position: (client, item, location, status, lot). Items that are not lot tracked behave exactly as before, with
-- no lot. The database refuses lot-tracked stock without a lot, so a flow that has not been made lot-aware fails loudly instead of
-- silently mixing batches. Receiving, inspection and returns are lot-aware from here; picking by first-expiry-first-out follows.

ALTER TABLE whse_items
    ADD COLUMN IF NOT EXISTS lot_tracked          BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS expiry_tracked       BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS min_shelf_life_days  INTEGER;

ALTER TABLE whse_items DROP CONSTRAINT IF EXISTS chk_whse_item_tracking;
ALTER TABLE whse_items ADD CONSTRAINT chk_whse_item_tracking CHECK (
    (NOT expiry_tracked OR lot_tracked) AND (min_shelf_life_days IS NULL OR (expiry_tracked AND min_shelf_life_days >= 0)));

CREATE TABLE whse_lots (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    client_id        UUID NOT NULL REFERENCES whse_clients (id),
    item_id          UUID NOT NULL REFERENCES whse_items (id),
    lot_number       VARCHAR(60) NOT NULL CHECK (length(trim(lot_number)) > 0),
    expiry_date      DATE,
    manufactured_date DATE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       UUID,
    CONSTRAINT uq_whse_lot UNIQUE (tenant_id, client_id, item_id, lot_number),
    CONSTRAINT chk_whse_lot_dates CHECK (manufactured_date IS NULL OR expiry_date IS NULL OR expiry_date >= manufactured_date)
);
CREATE INDEX idx_whse_lots_expiry ON whse_lots (tenant_id, expiry_date) WHERE expiry_date IS NOT NULL;

ALTER TABLE whse_inventory ADD COLUMN IF NOT EXISTS lot_id UUID REFERENCES whse_lots (id);
ALTER TABLE whse_inventory DROP CONSTRAINT IF EXISTS uq_whse_inventory_position;
CREATE UNIQUE INDEX uq_whse_inventory_position_lot ON whse_inventory
    (tenant_id, client_id, item_id, location_id, status, COALESCE(lot_id, '00000000-0000-0000-0000-000000000000'::uuid));
CREATE INDEX idx_whse_inventory_lot ON whse_inventory (lot_id) WHERE lot_id IS NOT NULL;

ALTER TABLE whse_stock_movements ADD COLUMN IF NOT EXISTS lot_id UUID REFERENCES whse_lots (id);
ALTER TABLE whse_receipt_events ADD COLUMN IF NOT EXISTS lot_id UUID REFERENCES whse_lots (id);
ALTER TABLE whse_return_receipts ADD COLUMN IF NOT EXISTS lot_id UUID REFERENCES whse_lots (id);

CREATE OR REPLACE FUNCTION whse_inventory_lot_rules() RETURNS trigger AS $$
DECLARE
    tracked BOOLEAN;
    lot_item UUID;
    lot_client UUID;
BEGIN
    IF NEW.lot_id IS NULL THEN
        IF NEW.qty_on_hand > 0 THEN
            SELECT lot_tracked INTO tracked FROM whse_items WHERE id = NEW.item_id;
            IF tracked THEN
                RAISE EXCEPTION 'This item is lot tracked, so its stock must carry a lot number';
            END IF;
        END IF;
    ELSE
        SELECT item_id, client_id INTO lot_item, lot_client FROM whse_lots WHERE id = NEW.lot_id;
        IF lot_item IS DISTINCT FROM NEW.item_id OR lot_client IS DISTINCT FROM NEW.client_id THEN
            RAISE EXCEPTION 'That lot does not belong to this client and item';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_whse_inventory_lot_rules ON whse_inventory;
CREATE TRIGGER trg_whse_inventory_lot_rules BEFORE INSERT OR UPDATE ON whse_inventory
    FOR EACH ROW EXECUTE FUNCTION whse_inventory_lot_rules();

-- The ledger reconciliation now follows the lot as well.
DROP VIEW IF EXISTS whse_ledger_reconciliation;
CREATE VIEW whse_ledger_reconciliation AS
WITH ledger AS (
    SELECT tenant_id, client_id, item_id, location_id, status, lot_id, SUM(qty_change) AS total
    FROM whse_stock_movements GROUP BY tenant_id, client_id, item_id, location_id, status, lot_id)
SELECT i.tenant_id, i.client_id, i.item_id, i.location_id, i.status,
       i.qty_on_hand AS balance, COALESCE(l.total, 0) AS ledger_total, i.qty_on_hand - COALESCE(l.total, 0) AS difference,
       i.lot_id
FROM whse_inventory i
LEFT JOIN ledger l ON l.tenant_id = i.tenant_id AND l.client_id = i.client_id AND l.item_id = i.item_id
                  AND l.location_id = i.location_id AND l.status = i.status AND l.lot_id IS NOT DISTINCT FROM i.lot_id
WHERE i.qty_on_hand <> COALESCE(l.total, 0)
UNION ALL
SELECT l.tenant_id, l.client_id, l.item_id, l.location_id, l.status, 0, l.total, -l.total, l.lot_id
FROM ledger l
WHERE l.total <> 0
  AND NOT EXISTS (SELECT 1 FROM whse_inventory i
                  WHERE i.tenant_id = l.tenant_id AND i.client_id = l.client_id AND i.item_id = l.item_id
                    AND i.location_id = l.location_id AND i.status = l.status AND i.lot_id IS NOT DISTINCT FROM l.lot_id);
