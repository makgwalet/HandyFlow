-- Match a forecourt's receipts to the dispatches that supplied it.
-- 1) A dispatch may name the forecourt (a depot) it is delivered to. Optional, so existing dispatches and customer or vehicle issues are unchanged.
ALTER TABLE fuel_dispatches ADD COLUMN destination_depot_id UUID REFERENCES fuel_depots (id);
CREATE INDEX ix_fuel_dispatches_destination ON fuel_dispatches (tenant_id, destination_depot_id) WHERE destination_depot_id IS NOT NULL;

-- 2) A person links a receipt in a forecourt tank to the dispatch it came from. A receipt belongs to at most one dispatch; a dispatch may be received in several receipts.
CREATE TABLE fuel_dispatch_receipt_matches (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    dispatch_id  UUID NOT NULL REFERENCES fuel_dispatches (id),
    receipt_id   UUID NOT NULL REFERENCES fuel_receipts (id),
    matched_by   UUID,
    matched_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_fuel_dispatch_receipt_matches_receipt ON fuel_dispatch_receipt_matches (tenant_id, receipt_id);
CREATE INDEX ix_fuel_dispatch_receipt_matches_dispatch ON fuel_dispatch_receipt_matches (tenant_id, dispatch_id);
