-- Supply Chain: goods receipt reversal and the status / movement-type values it needs.

ALTER TABLE sc_goods_receipts ADD COLUMN IF NOT EXISTS reversed_at     TIMESTAMPTZ;
ALTER TABLE sc_goods_receipts ADD COLUMN IF NOT EXISTS reversed_by     UUID;
ALTER TABLE sc_goods_receipts ADD COLUMN IF NOT EXISTS reversal_reason VARCHAR(500);

-- V86 created inline CHECK constraints (auto-named <table>_<column>_check). Replace them with
-- versions that allow the new values, rather than leaving the columns unconstrained.
ALTER TABLE sc_goods_receipts DROP CONSTRAINT IF EXISTS sc_goods_receipts_status_check;
ALTER TABLE sc_goods_receipts DROP CONSTRAINT IF EXISTS chk_sc_gr_status;
ALTER TABLE sc_goods_receipts
    ADD CONSTRAINT chk_sc_gr_status CHECK (status IN ('DRAFT','POSTED','REVERSED','CANCELLED'));

ALTER TABLE sc_stock_movements DROP CONSTRAINT IF EXISTS sc_stock_movements_movement_type_check;
ALTER TABLE sc_stock_movements DROP CONSTRAINT IF EXISTS chk_sc_stock_movement_type;
ALTER TABLE sc_stock_movements
    ADD CONSTRAINT chk_sc_stock_movement_type CHECK (movement_type IN (
        'OPENING','PURCHASE','PURCHASE_REVERSAL','SALE','TRANSFER_IN','TRANSFER_OUT',
        'ADJUSTMENT_UP','ADJUSTMENT_DOWN','WASTE','RETURN_IN','RETURN_OUT','PRODUCTION_IN','PRODUCTION_OUT'));

-- A receipt can be reversed only once, whatever two people click at the same moment.
ALTER TABLE sc_goods_receipts DROP CONSTRAINT IF EXISTS chk_sc_gr_reversal_consistent;
ALTER TABLE sc_goods_receipts
    ADD CONSTRAINT chk_sc_gr_reversal_consistent CHECK ((status = 'REVERSED') = (reversed_at IS NOT NULL));

CREATE INDEX IF NOT EXISTS idx_sc_gr_lines_po_line ON sc_gr_lines(po_line_id);
