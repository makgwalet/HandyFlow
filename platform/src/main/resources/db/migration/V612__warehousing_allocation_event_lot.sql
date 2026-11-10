-- Allocation events record which lot a reservation was made against. Events written before this
-- migration cannot be attributed to a lot (they carry no inventory id) and keep lot_id NULL.
ALTER TABLE whse_allocation_events ADD COLUMN lot_id UUID REFERENCES whse_lots (id);
CREATE INDEX idx_whse_alloc_events_lot ON whse_allocation_events (tenant_id, lot_id) WHERE lot_id IS NOT NULL;
