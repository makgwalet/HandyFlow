-- Inspections now say why they were done: the usual pre-use walk-round, or the condition check when a machine is handed over to a
-- customer or comes back from hire. Existing rows are all pre-use walk-rounds.
ALTER TABLE earthmoving_inspections ADD COLUMN IF NOT EXISTS inspection_type VARCHAR(12) NOT NULL DEFAULT 'PRE_USE';
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_em_inspection_type') THEN
        ALTER TABLE earthmoving_inspections ADD CONSTRAINT chk_em_inspection_type CHECK (inspection_type IN ('PRE_USE', 'HANDOVER', 'RETURN'));
    END IF;
END $$;
