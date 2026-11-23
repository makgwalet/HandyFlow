-- Chunk 30: kg per bag. An item counted in bags, bales or sacks may say how many kg one holds, so feed given in kg
-- can be taken off it (120 kg = 2.4 bags) and its cost per kg worked out. Each stock movement keeps the factor used.
ALTER TABLE ag_inventory_items ADD COLUMN IF NOT EXISTS pack_size_kg NUMERIC(10,3);
ALTER TABLE ag_stock_movements ADD COLUMN IF NOT EXISTS pack_size_kg NUMERIC(10,3);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_ag_inventory_pack_size') THEN
        ALTER TABLE ag_inventory_items ADD CONSTRAINT chk_ag_inventory_pack_size
            CHECK (pack_size_kg IS NULL OR (pack_size_kg > 0 AND pack_size_kg <= 10000)) NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_ag_stock_movement_pack_size') THEN
        ALTER TABLE ag_stock_movements ADD CONSTRAINT chk_ag_stock_movement_pack_size
            CHECK (pack_size_kg IS NULL OR pack_size_kg > 0) NOT VALID;
    END IF;
END $$;
