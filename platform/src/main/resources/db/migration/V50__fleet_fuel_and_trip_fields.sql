-- V50__fleet_fuel_and_trip_fields.sql
-- Confirmed table names from V14__fleet_vehicles.sql:
--   fleet_vehicles        (already has: vin, colour, roadworthy_expiry, insurance_expiry,
--                          next_service_km, invoice_ref — do NOT re-add those)
--   fleet_services        (NOT fleet_vehicle_services)
--   fleet_trips

-- ── Fuel fill-up table (NEW) ──────────────────────────────────────────────────
CREATE TABLE fleet_fuel_fillups (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID          NOT NULL,
    vehicle_id          UUID          NOT NULL,
    filled_at           DATE          NOT NULL,
    litres              NUMERIC(10,2) NOT NULL,
    price_per_litre     NUMERIC(10,3),
    total_cost          NUMERIC(15,2),
    odometer_at_fillup  INTEGER,
    station             VARCHAR(255),
    receipt_ref         VARCHAR(100),
    full_tank           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_fleet_fuel_fillups PRIMARY KEY (id),
    CONSTRAINT fk_fuel_vehicle FOREIGN KEY (vehicle_id) REFERENCES fleet_vehicles(id)
);

CREATE INDEX idx_fuel_fillups_vehicle
    ON fleet_fuel_fillups (vehicle_id, filled_at DESC);

-- ── fleet_trips — add trip_type, status, notes (new columns only) ─────────────
ALTER TABLE fleet_trips
    ADD COLUMN IF NOT EXISTS trip_type VARCHAR(20) NOT NULL DEFAULT 'BUSINESS',
    ADD COLUMN IF NOT EXISTS status    VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    ADD COLUMN IF NOT EXISTS notes     TEXT;

-- ── fleet_vehicles — add only columns NOT already in V14 ─────────────────────
-- V14 already has: vin, colour, roadworthy_expiry, insurance_expiry,
--                  tank_capacity_litres, daily_rate, notes
-- Adding only what is genuinely missing:
ALTER TABLE fleet_vehicles
    ADD COLUMN IF NOT EXISTS service_interval_days INTEGER,
    ADD COLUMN IF NOT EXISTS assigned_driver_name  VARCHAR(255);

-- Fix the status CHECK constraint — V14 allows 'IN_USE','SERVICE','BREAKDOWN'
-- but the new frontend/service use 'ON_TRIP','MAINTENANCE' instead.
-- Drop and replace so both old and new values are accepted during transition.
ALTER TABLE fleet_vehicles
    DROP CONSTRAINT IF EXISTS chk_vehicle_status;

ALTER TABLE fleet_vehicles
    ADD CONSTRAINT chk_vehicle_status CHECK (
        status IN ('AVAILABLE','ON_TRIP','IN_USE','MAINTENANCE','SERVICE','BREAKDOWN','RETIRED')
    );

-- Fix vehicle_type CHECK — add MINIBUS, COMPACTOR which the UI now supports
ALTER TABLE fleet_vehicles
    DROP CONSTRAINT IF EXISTS chk_vehicle_type;

ALTER TABLE fleet_vehicles
    ADD CONSTRAINT chk_vehicle_type CHECK (
        vehicle_type IN ('BAKKIE','SEDAN','SUV','TRUCK','VAN','BUS','MINIBUS','MOTORCYCLE','OTHER')
    );

-- Fix fuel_type CHECK — add LPG which the UI now offers
ALTER TABLE fleet_vehicles
    DROP CONSTRAINT IF EXISTS chk_fuel_type;

ALTER TABLE fleet_vehicles
    ADD CONSTRAINT chk_fuel_type CHECK (
        fuel_type IN ('DIESEL','PETROL','ELECTRIC','HYBRID','LPG','GAS','OTHER')
    );