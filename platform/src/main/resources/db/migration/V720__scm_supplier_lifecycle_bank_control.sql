-- Renumber to your next Flyway version (must run AFTER the chunk 1 migration).

ALTER TABLE sc_goods_receipts ADD COLUMN IF NOT EXISTS reversed_at     TIMESTAMPTZ;
ALTER TABLE sc_goods_receipts ADD COLUMN IF NOT EXISTS reversed_by     UUID;
ALTER TABLE sc_goods_receipts ADD COLUMN IF NOT EXISTS reversal_reason VARCHAR(500);

-- The new GR status REVERSED and stock movement type PURCHASE_REVERSAL may be rejected by
-- CHECK constraints created in the original SCM migration. Drop any CHECK that mentions
-- those columns (names unknown), then re-add a permissive one for the GR status.
DO $$
DECLARE r RECORD;
BEGIN
    FOR r IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'sc_goods_receipts'::regclass AND contype = 'c'
               AND pg_get_constraintdef(oid) ILIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE sc_goods_receipts DROP CONSTRAINT %I', r.conname);
    END LOOP;

    FOR r IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'sc_stock_movements'::regclass AND contype = 'c'
               AND pg_get_constraintdef(oid) ILIKE '%movement_type%'
    LOOP
        EXECUTE format('ALTER TABLE sc_stock_movements DROP CONSTRAINT %I', r.conname);
    END LOOP;
END $$;

ALTER TABLE sc_goods_receipts
    ADD CONSTRAINT chk_sc_gr_status CHECK (status IN ('DRAFT','POSTED','REVERSED','CANCELLED'));

CREATE INDEX IF NOT EXISTS idx_sc_gr_lines_po_line ON sc_gr_lines(po_line_id);
CREATE INDEX IF NOT EXISTS idx_sc_stock_mv_reference ON sc_stock_movements(reference_type, reference_id);