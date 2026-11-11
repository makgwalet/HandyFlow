-- Fuel: per-tenant settings (cost-to-serve rates, PO delivery tolerance), delivery-site distance, and closing a part-received PO short.

CREATE TABLE IF NOT EXISTS fuel_settings (
    id                        UUID PRIMARY KEY,
    tenant_id                 UUID NOT NULL,
    driver_cost_per_hour      NUMERIC(10,2) NOT NULL DEFAULT 0,
    vehicle_cost_per_km       NUMERIC(10,4) NOT NULL DEFAULT 0,
    fixed_cost_per_delivery   NUMERIC(10,2) NOT NULL DEFAULT 0,
    overhead_pct_of_revenue   NUMERIC(5,2)  NOT NULL DEFAULT 0,
    default_trip_hours        NUMERIC(5,2)  NOT NULL DEFAULT 2,
    po_over_tolerance_pct     NUMERIC(5,2)  NOT NULL DEFAULT 2,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                   BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_fuel_settings_rates CHECK (driver_cost_per_hour >= 0 AND vehicle_cost_per_km >= 0 AND fixed_cost_per_delivery >= 0),
    CONSTRAINT chk_fuel_settings_pct CHECK (overhead_pct_of_revenue BETWEEN 0 AND 100),
    CONSTRAINT chk_fuel_settings_hours CHECK (default_trip_hours > 0 AND default_trip_hours <= 24),
    CONSTRAINT chk_fuel_settings_tol CHECK (po_over_tolerance_pct BETWEEN 0 AND 10)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_fuel_settings_tenant ON fuel_settings (tenant_id);

ALTER TABLE fuel_customer_sites ADD COLUMN IF NOT EXISTS distance_km NUMERIC(8,1);
ALTER TABLE fuel_customer_sites DROP CONSTRAINT IF EXISTS chk_fuel_site_distance;
ALTER TABLE fuel_customer_sites ADD CONSTRAINT chk_fuel_site_distance CHECK (distance_km IS NULL OR (distance_km >= 0 AND distance_km <= 5000));

ALTER TABLE fuel_purchase_orders ADD COLUMN IF NOT EXISTS close_reason VARCHAR(300);
ALTER TABLE fuel_purchase_orders DROP CONSTRAINT IF EXISTS chk_fuel_po_status;
ALTER TABLE fuel_purchase_orders ADD CONSTRAINT chk_fuel_po_status
    CHECK (status IN ('DRAFT','ORDERED','PART_RECEIVED','RECEIVED','CLOSED_SHORT','CANCELLED'));
