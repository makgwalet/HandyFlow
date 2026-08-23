-- NUMBERING NOTE: same placeholder caution as every migration this
-- session — confirm the real next version number before running.

-- backlog 5.1 — Fuel ↔ Fleet reconciliation. Nullable and unique:
-- NULL for every fillup logged manually via FleetService.logFuel()
-- (unchanged, existing behaviour); populated only for fillups created
-- by the new FleetFuelDispatchEventHandler listener. The UNIQUE
-- constraint is what makes that listener idempotent — if
-- FuelDispatchedToVehicleEvent is ever delivered twice for the same
-- dispatch (a real possibility with Spring Modulith's at-least-once
-- event semantics), the second insert attempt is rejected rather than
-- double-counting the same litres into cost-per-km.
ALTER TABLE fleet_fuel_fillups ADD COLUMN source_fuel_dispatch_id UUID UNIQUE;