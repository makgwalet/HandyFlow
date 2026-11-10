-- V605: Warehousing item master. Barcode (GTIN), dimensions, weight, cost, min/max levels and handling flags.
-- All new columns are optional, so existing items and every existing caller keep working unchanged.
-- Cost is the client's unit cost in the tenant currency; it feeds stock value on the operations summary.

ALTER TABLE whse_items
    ADD COLUMN IF NOT EXISTS barcode          VARCHAR(14),
    ADD COLUMN IF NOT EXISTS length_mm        NUMERIC(10,1),
    ADD COLUMN IF NOT EXISTS width_mm         NUMERIC(10,1),
    ADD COLUMN IF NOT EXISTS height_mm        NUMERIC(10,1),
    ADD COLUMN IF NOT EXISTS weight_kg        NUMERIC(10,3),
    ADD COLUMN IF NOT EXISTS unit_cost        NUMERIC(14,4),
    ADD COLUMN IF NOT EXISTS min_qty          NUMERIC(12,3),
    ADD COLUMN IF NOT EXISTS max_qty          NUMERIC(12,3),
    ADD COLUMN IF NOT EXISTS temperature_class VARCHAR(20) NOT NULL DEFAULT 'AMBIENT',
    ADD COLUMN IF NOT EXISTS hazardous        BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS fragile          BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS stackable        BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE whse_items DROP CONSTRAINT IF EXISTS chk_whse_item_barcode;
ALTER TABLE whse_items ADD CONSTRAINT chk_whse_item_barcode CHECK (barcode IS NULL OR barcode ~ '^[0-9]{8}$|^[0-9]{12,14}$');
ALTER TABLE whse_items DROP CONSTRAINT IF EXISTS chk_whse_item_measures;
ALTER TABLE whse_items ADD CONSTRAINT chk_whse_item_measures CHECK (
    (length_mm IS NULL OR length_mm > 0) AND (width_mm IS NULL OR width_mm > 0) AND (height_mm IS NULL OR height_mm > 0)
    AND (weight_kg IS NULL OR weight_kg > 0) AND (unit_cost IS NULL OR unit_cost >= 0));
ALTER TABLE whse_items DROP CONSTRAINT IF EXISTS chk_whse_item_levels;
ALTER TABLE whse_items ADD CONSTRAINT chk_whse_item_levels CHECK (
    (min_qty IS NULL OR min_qty >= 0) AND (max_qty IS NULL OR max_qty >= 0) AND (min_qty IS NULL OR max_qty IS NULL OR max_qty >= min_qty));
ALTER TABLE whse_items DROP CONSTRAINT IF EXISTS chk_whse_item_temperature;
ALTER TABLE whse_items ADD CONSTRAINT chk_whse_item_temperature CHECK (temperature_class IN ('AMBIENT','CHILLED','FROZEN','CONTROLLED_ROOM'));

-- A barcode identifies one item per client among live items. Two clients may legitimately stock the same product.
CREATE UNIQUE INDEX IF NOT EXISTS uq_whse_item_barcode
    ON whse_items (tenant_id, client_id, barcode) WHERE barcode IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_whse_items_barcode_lookup
    ON whse_items (tenant_id, barcode) WHERE barcode IS NOT NULL AND deleted_at IS NULL;
