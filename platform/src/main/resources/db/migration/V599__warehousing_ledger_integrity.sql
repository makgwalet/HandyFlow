-- V599: Warehousing slice 1, inventory ledger integrity (docs/warehousing/WAREHOUSING_PARITY_MATRIX_2026-11-06.md, engine 3).
--
-- Database-level guarantees only; no application behaviour changes in this migration.
--   1. whse_stock_movements becomes append-only (same approach as audit_events, V409). A correction is a new, opposite
--      movement, never an edit. Nothing in the application updates or deletes movements today.
--   2. The movement type list is widened so later slices (transfer, put-away, returns, damage, quarantine, counts) can post
--      to the same ledger. The three types in use today are unchanged.
--   3. idempotency_key: a caller may send a key so a retried receipt/pick/adjustment is recorded once (unique per tenant).
--   4. qty_after = qty_before + qty_change is enforced for new rows (NOT VALID: existing rows are not re-checked here).
--   5. whse_ledger_reconciliation: read-only view that lists every stock position whose on-hand balance does not equal the
--      sum of its movements. An empty result means every balance can be rebuilt from the ledger.

-- 1. append-only
CREATE OR REPLACE FUNCTION whse_stock_movements_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'whse_stock_movements is append-only: record a compensating movement instead';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_whse_stock_movements_no_change ON whse_stock_movements;
CREATE TRIGGER trg_whse_stock_movements_no_change
    BEFORE UPDATE OR DELETE ON whse_stock_movements
    FOR EACH ROW EXECUTE FUNCTION whse_stock_movements_append_only();

-- 2. wider movement types
ALTER TABLE whse_stock_movements DROP CONSTRAINT IF EXISTS chk_whse_movement_type;
ALTER TABLE whse_stock_movements ADD CONSTRAINT chk_whse_movement_type CHECK (movement_type IN (
    'RECEIPT', 'PICK', 'ADJUSTMENT',
    'PUTAWAY', 'TRANSFER', 'SHIP', 'RETURN', 'DAMAGE', 'LOSS', 'FOUND', 'QUARANTINE', 'RELEASE', 'COUNT_VARIANCE'));

-- 3. idempotency
ALTER TABLE whse_stock_movements ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);
CREATE UNIQUE INDEX IF NOT EXISTS uq_whse_movements_idempotency
    ON whse_stock_movements (tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL;

-- 4. arithmetic of each row
ALTER TABLE whse_stock_movements DROP CONSTRAINT IF EXISTS chk_whse_movement_arithmetic;
ALTER TABLE whse_stock_movements ADD CONSTRAINT chk_whse_movement_arithmetic
    CHECK (qty_after = qty_before + qty_change) NOT VALID;

-- 5. reconciliation view
CREATE OR REPLACE VIEW whse_ledger_reconciliation AS
SELECT i.tenant_id, i.client_id, i.item_id, i.location_id,
       i.qty_on_hand                                   AS balance,
       COALESCE(m.total, 0)                            AS ledger_total,
       i.qty_on_hand - COALESCE(m.total, 0)            AS difference
FROM whse_inventory i
LEFT JOIN (SELECT tenant_id, client_id, item_id, location_id, SUM(qty_change) AS total
           FROM whse_stock_movements
           GROUP BY tenant_id, client_id, item_id, location_id) m
       ON m.tenant_id = i.tenant_id AND m.client_id = i.client_id
      AND m.item_id = i.item_id AND m.location_id = i.location_id
WHERE i.qty_on_hand <> COALESCE(m.total, 0)
UNION ALL
-- ledger rows with no balance row at all
SELECT m.tenant_id, m.client_id, m.item_id, m.location_id, 0, m.total, -m.total
FROM (SELECT tenant_id, client_id, item_id, location_id, SUM(qty_change) AS total
      FROM whse_stock_movements GROUP BY tenant_id, client_id, item_id, location_id) m
WHERE NOT EXISTS (SELECT 1 FROM whse_inventory i
                  WHERE i.tenant_id = m.tenant_id AND i.client_id = m.client_id
                    AND i.item_id = m.item_id AND i.location_id = m.location_id)
  AND m.total <> 0;
