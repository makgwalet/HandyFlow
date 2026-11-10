-- A location can carry its own scannable barcode. When it has none, scanning its code works the same way.
ALTER TABLE whse_locations ADD COLUMN IF NOT EXISTS barcode VARCHAR(60);
ALTER TABLE whse_locations DROP CONSTRAINT IF EXISTS chk_whse_location_barcode;
ALTER TABLE whse_locations ADD CONSTRAINT chk_whse_location_barcode CHECK (barcode IS NULL OR (length(barcode) BETWEEN 1 AND 60 AND barcode !~ '\s'));
CREATE UNIQUE INDEX IF NOT EXISTS uq_whse_location_barcode ON whse_locations (tenant_id, barcode) WHERE barcode IS NOT NULL AND deleted_at IS NULL;
