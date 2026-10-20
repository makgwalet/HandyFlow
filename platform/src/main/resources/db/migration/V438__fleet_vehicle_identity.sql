-- Rename to the next free Flyway version, e.g. V123__fleet_vehicle_identity.sql
--
-- BEFORE RUNNING: the unique indexes at the bottom will fail if existing data already
-- has duplicate VINs within a tenant. Check first:
--   SELECT tenant_id, vin, COUNT(*) FROM fleet_vehicles
--   WHERE deleted_at IS NULL AND vin IS NOT NULL AND vin <> ''
--   GROUP BY tenant_id, vin HAVING COUNT(*) > 1;
-- Also normalise blank VINs to NULL:
UPDATE fleet_vehicles SET vin = NULL WHERE vin IS NOT NULL AND TRIM(vin) = '';

ALTER TABLE fleet_vehicles
    ADD COLUMN engine_number            VARCHAR(50),
    ADD COLUMN variant                  VARCHAR(100),
    ADD COLUMN body_type                VARCHAR(50),
    ADD COLUMN transmission             VARCHAR(20),
    ADD COLUMN drive_type               VARCHAR(20),
    ADD COLUMN axle_count               INTEGER,
    ADD COLUMN gvm_kg                   INTEGER,
    ADD COLUMN tare_kg                  INTEGER,
    ADD COLUMN payload_kg               INTEGER,
    ADD COLUMN gcm_kg                   INTEGER,
    ADD COLUMN engine_hours             NUMERIC(10, 1) NOT NULL DEFAULT 0,
    ADD COLUMN acquisition_method       VARCHAR(20),
    ADD COLUMN current_book_value       NUMERIC(15, 2),
    ADD COLUMN current_estimated_value  NUMERIC(15, 2),
    ADD COLUMN valued_at                DATE;

-- purchase_date and purchase_price already exist on fleet_vehicles and are reused
-- as "acquisition date" / "purchase price" — no duplicate columns.

ALTER TABLE fleet_vehicles
    ADD CONSTRAINT chk_fleet_vehicles_mass_tare_gvm
        CHECK (tare_kg IS NULL OR gvm_kg IS NULL OR tare_kg <= gvm_kg),
    ADD CONSTRAINT chk_fleet_vehicles_mass_gvm_gcm
        CHECK (gvm_kg IS NULL OR gcm_kg IS NULL OR gvm_kg <= gcm_kg);

-- A VIN / engine number identifies one physical vehicle: unique per tenant among live records.
CREATE UNIQUE INDEX uq_fleet_vehicles_tenant_vin
    ON fleet_vehicles (tenant_id, vin)
    WHERE deleted_at IS NULL AND vin IS NOT NULL;

CREATE UNIQUE INDEX uq_fleet_vehicles_tenant_engine_number
    ON fleet_vehicles (tenant_id, engine_number)
    WHERE deleted_at IS NULL AND engine_number IS NOT NULL;