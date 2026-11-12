-- Phase 8d: a forecourt is the depot that holds the pump tanks. Record which depot supplies it, so forecourt sales can be
-- attributed to the supplying depot. Nullable: existing depots are unchanged. Self-reference only; no new master record.
ALTER TABLE fuel_depots ADD COLUMN supplying_depot_id UUID REFERENCES fuel_depots (id);
ALTER TABLE fuel_depots ADD CONSTRAINT ck_fuel_depots_not_self_supplied CHECK (supplying_depot_id IS NULL OR supplying_depot_id <> id);
CREATE INDEX ix_fuel_depots_supplier ON fuel_depots (tenant_id, supplying_depot_id) WHERE supplying_depot_id IS NOT NULL;
