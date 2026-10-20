-- Rename to the next free Flyway version, e.g. V131__fleet_fuel_analytics.sql

-- Optional per-vehicle override of "what consumption should be". Without a row (or with a NULL
-- expected figure) the analytics use each vehicle's own rolling baseline.
CREATE TABLE fleet_vehicle_fuel_settings (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID         NOT NULL,
    vehicle_id                  UUID         NOT NULL UNIQUE REFERENCES fleet_vehicles (id),
    expected_litres_per_100km   NUMERIC(5, 1),
    tolerance_percent           INTEGER,                 -- NULL = built-in default (20%)
    updated_at                  TIMESTAMPTZ  NOT NULL,
    updated_by                  UUID,
    version                     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_fuel_settings_expected CHECK (expected_litres_per_100km IS NULL OR expected_litres_per_100km > 0),
    CONSTRAINT chk_fuel_settings_tolerance CHECK (tolerance_percent IS NULL OR tolerance_percent BETWEEN 5 AND 100)
);

-- Indexes for the analytics queries on the existing fuel table.
CREATE INDEX IF NOT EXISTS idx_fleet_fuel_fillups_vehicle_date ON fleet_fuel_fillups (vehicle_id, filled_at);
CREATE INDEX IF NOT EXISTS idx_fleet_fuel_fillups_tenant_date  ON fleet_fuel_fillups (tenant_id, filled_at);
CREATE INDEX IF NOT EXISTS idx_fleet_fuel_fillups_created      ON fleet_fuel_fillups (created_at);
CREATE INDEX IF NOT EXISTS idx_fleet_fuel_fillups_receipt      ON fleet_fuel_fillups (tenant_id, UPPER(TRIM(receipt_ref)))
    WHERE receipt_ref IS NOT NULL;