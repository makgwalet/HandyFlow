-- A truck stop stay or tab may point at one of the company's own Fleet vehicles. Optional; Fleet stays the only owner of vehicle data.
-- No foreign key: Fleet is another module. Unlike a tanker, a vehicle may appear on many stays and tabs.
ALTER TABLE fuel_truck_stop_stays ADD COLUMN fleet_vehicle_id UUID;
ALTER TABLE fuel_truck_stop_tabs  ADD COLUMN fleet_vehicle_id UUID;
CREATE INDEX ix_fuel_ts_stays_fleet ON fuel_truck_stop_stays (tenant_id, fleet_vehicle_id) WHERE fleet_vehicle_id IS NOT NULL;
CREATE INDEX ix_fuel_ts_tabs_fleet  ON fuel_truck_stop_tabs  (tenant_id, fleet_vehicle_id) WHERE fleet_vehicle_id IS NOT NULL;
