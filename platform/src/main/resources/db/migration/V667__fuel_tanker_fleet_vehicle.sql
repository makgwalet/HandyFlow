-- C03: a tanker can point at the vehicle in Fleet, so there is one vehicle master (maintenance, documents, odometer stay in Fleet).
-- Additive and optional: tankers without a link behave exactly as before. No foreign key on purpose: Fleet and Fuel are separate
-- modules; the link is checked in the application against the same tenant.
ALTER TABLE fuel_tankers ADD COLUMN fleet_vehicle_id UUID;

-- A Fleet vehicle backs at most one live tanker.
CREATE UNIQUE INDEX uq_fuel_tanker_fleet_vehicle ON fuel_tankers (tenant_id, fleet_vehicle_id)
    WHERE deleted_at IS NULL AND fleet_vehicle_id IS NOT NULL;
