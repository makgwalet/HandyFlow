-- V610: Warehousing floor map. A location gets its place in the building: aisle / bay / level, a type, a walking order for picking,
-- and a cell on the floor grid. Levels of the same bay share one cell, so the map shows a rack once and lists its levels.
-- All columns are optional: existing locations keep working and simply show as "not placed" until they are given a cell.

ALTER TABLE whse_locations
    ADD COLUMN IF NOT EXISTS aisle         VARCHAR(20),
    ADD COLUMN IF NOT EXISTS bay           INTEGER,
    ADD COLUMN IF NOT EXISTS level         INTEGER,
    ADD COLUMN IF NOT EXISTS location_type VARCHAR(10) NOT NULL DEFAULT 'BIN',
    ADD COLUMN IF NOT EXISTS pick_sequence INTEGER,
    ADD COLUMN IF NOT EXISTS pos_x         INTEGER,
    ADD COLUMN IF NOT EXISTS pos_y         INTEGER;

ALTER TABLE whse_locations DROP CONSTRAINT IF EXISTS chk_whse_location_type;
ALTER TABLE whse_locations ADD CONSTRAINT chk_whse_location_type CHECK (location_type IN ('BIN','SHELF','PALLET','FLOOR','DOCK','STAGING'));

ALTER TABLE whse_locations DROP CONSTRAINT IF EXISTS chk_whse_location_layout;
ALTER TABLE whse_locations ADD CONSTRAINT chk_whse_location_layout CHECK (
    (pos_x IS NULL) = (pos_y IS NULL)
    AND (pos_x IS NULL OR (pos_x >= 0 AND pos_y >= 0))
    AND (bay IS NULL OR bay >= 1)
    AND (level IS NULL OR level >= 1)
    AND (pick_sequence IS NULL OR pick_sequence >= 0));

-- One location per floor cell and level. A soft-deleted location frees its cell (the code stays reserved by uq_whse_location_code).
CREATE UNIQUE INDEX IF NOT EXISTS uq_whse_location_cell ON whse_locations (tenant_id, pos_x, pos_y, COALESCE(level, 0))
    WHERE pos_x IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_whse_locations_pick_seq ON whse_locations (tenant_id, pick_sequence) WHERE deleted_at IS NULL;
