-- D01: group pumps into dispensers with numbered nozzles. Additive and optional: a pump with neither set behaves exactly as before.
-- Each existing pump record is already one metered dispensing point (its own meter, tank and price), i.e. one nozzle.
ALTER TABLE fuel_pumps
    ADD COLUMN dispenser_label VARCHAR(40),
    ADD COLUMN nozzle_number   INTEGER;

ALTER TABLE fuel_pumps
    ADD CONSTRAINT chk_fuel_pumps_nozzle_number CHECK (nozzle_number IS NULL OR (nozzle_number BETWEEN 1 AND 20)),
    ADD CONSTRAINT chk_fuel_pumps_nozzle_needs_dispenser CHECK (nozzle_number IS NULL OR dispenser_label IS NOT NULL);

-- One nozzle number per dispenser, among pumps that still exist.
CREATE UNIQUE INDEX uq_fuel_pumps_dispenser_nozzle ON fuel_pumps (tenant_id, upper(dispenser_label), nozzle_number)
    WHERE deleted_at IS NULL AND dispenser_label IS NOT NULL AND nozzle_number IS NOT NULL;
