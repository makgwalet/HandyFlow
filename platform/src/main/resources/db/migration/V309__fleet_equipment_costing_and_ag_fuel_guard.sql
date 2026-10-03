-- V309__fleet_equipment_costing_and_ag_fuel_guard.sql
--
-- ADR-001, W4: equipment and fuel costs for Agriculture.
--
-- 1. fleet_vehicles gets the two numbers equipment costing needs, both optional until someone sets them:
--      engine_hours             the engine-hours meter reading (tractors and harvesters are run by hours, not kilometres)
--      operating_rate_per_hour  what an hour of use costs to keep the machine running: SERVICE AND REPAIRS ONLY.
--                               Deliberately NOT fuel (allocated separately, from fuel dispatches) and NOT depreciation, so nothing is counted twice.
--    Fleet owns these; Agriculture reads them through FleetFacade and snapshots the rate into the cost ledger when it costs the use.
--
-- 2. ag_cost_entries: the same fuel dispatch cannot be allocated to the same target twice. The application also refuses to allocate a dispatch
--    that already has an ACTIVE fuel entry; this index backs that at the database. It is per target (not per dispatch) because a dispatch may be
--    split across several targets as one allocation group, so there are several rows with the same source_ref. Reversing a group frees the dispatch.
ALTER TABLE fleet_vehicles
    ADD COLUMN engine_hours             NUMERIC(10,1),
    ADD COLUMN operating_rate_per_hour  NUMERIC(12,4),
    ADD CONSTRAINT chk_fleet_vehicles_engine_hours   CHECK (engine_hours IS NULL OR engine_hours >= 0),
    ADD CONSTRAINT chk_fleet_vehicles_operating_rate CHECK (operating_rate_per_hour IS NULL OR operating_rate_per_hour >= 0);

CREATE UNIQUE INDEX uq_ag_cost_entries_fuel_source
    ON ag_cost_entries (tenant_id, source_ref, target_type, target_id)
    WHERE source_type = 'FUEL_DISPATCH' AND status = 'ACTIVE';
