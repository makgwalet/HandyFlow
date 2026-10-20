-- Rename to the next free Flyway version, e.g. V133__fleet_assets.sql

-- Equipment profile: 1:1 with fleet_vehicles, only for assets that need more than a road vehicle does.
-- A vehicle with no row here is a plain ROAD_VEHICLE.
CREATE TABLE fleet_asset_profiles (
    id                       UUID PRIMARY KEY,
    tenant_id                UUID           NOT NULL,
    vehicle_id               UUID           NOT NULL UNIQUE REFERENCES fleet_vehicles (id),
    asset_class              VARCHAR(20)    NOT NULL,   -- ROAD_VEHICLE | HEAVY_VEHICLE | TRACTOR | PLANT | TRAILER | FORKLIFT | SPECIALISED
    pto_hours                NUMERIC(10, 1) NOT NULL DEFAULT 0,
    hydraulic_hours          NUMERIC(10, 1) NOT NULL DEFAULT 0,
    operating_rate_per_hour  NUMERIC(15, 2),
    target_hours_per_day     NUMERIC(4, 1)  NOT NULL DEFAULT 8.0,
    service_interval_hours   INTEGER,
    last_service_hours       NUMERIC(10, 1),
    coupled_to_vehicle_id    UUID           REFERENCES fleet_vehicles (id),   -- trailers: the vehicle hauling them
    site_name                VARCHAR(150),
    site_updated_at          TIMESTAMPTZ,
    axle_configuration       VARCHAR(50),
    operator_competency      VARCHAR(255),
    notes                    TEXT,
    created_at               TIMESTAMPTZ    NOT NULL,
    updated_at               TIMESTAMPTZ    NOT NULL,
    version                  BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT chk_fleet_asset_profiles_hours CHECK (pto_hours >= 0 AND hydraulic_hours >= 0),
    CONSTRAINT chk_fleet_asset_profiles_target CHECK (target_hours_per_day > 0 AND target_hours_per_day <= 24),
    CONSTRAINT chk_fleet_asset_profiles_not_self CHECK (coupled_to_vehicle_id IS NULL OR coupled_to_vehicle_id <> vehicle_id)
);
CREATE INDEX idx_fleet_asset_profiles_tenant_class ON fleet_asset_profiles (tenant_id, asset_class);
CREATE INDEX idx_fleet_asset_profiles_coupled ON fleet_asset_profiles (coupled_to_vehicle_id) WHERE coupled_to_vehicle_id IS NOT NULL;

-- Backfill from the free-text vehicle_type. REVIEW before running: only exact, unambiguous matches are mapped.
-- 'TRUCK' becomes HEAVY_VEHICLE — change that line if some of your trucks are light.
INSERT INTO fleet_asset_profiles (id, tenant_id, vehicle_id, asset_class, last_service_hours, created_at, updated_at)
SELECT gen_random_uuid(), v.tenant_id, v.id,
       CASE UPPER(TRIM(v.vehicle_type))
            WHEN 'TRACTOR'  THEN 'TRACTOR'
            WHEN 'FORKLIFT' THEN 'FORKLIFT'
            WHEN 'TRAILER'  THEN 'TRAILER'
            WHEN 'TRUCK'    THEN 'HEAVY_VEHICLE'
       END,
       NULL, NOW(), NOW()
FROM fleet_vehicles v
WHERE v.deleted_at IS NULL
  AND UPPER(TRIM(v.vehicle_type)) IN ('TRACTOR', 'FORKLIFT', 'TRAILER', 'TRUCK')
ON CONFLICT (vehicle_id) DO NOTHING;

-- Meter history: the basis for utilisation (hours worked in a period).
CREATE TABLE fleet_asset_meter_readings (
    id               UUID PRIMARY KEY,
    tenant_id        UUID           NOT NULL,
    vehicle_id       UUID           NOT NULL REFERENCES fleet_vehicles (id),
    recorded_at      TIMESTAMPTZ    NOT NULL,
    engine_hours     NUMERIC(10, 1) NOT NULL,
    pto_hours        NUMERIC(10, 1),
    hydraulic_hours  NUMERIC(10, 1),
    recorded_by      UUID
);
CREATE INDEX idx_fleet_asset_meter_readings_vehicle ON fleet_asset_meter_readings (tenant_id, vehicle_id, recorded_at DESC);

-- Implements / attachments: unregistered kit that is hitched to a tractor, plant or forklift.
CREATE TABLE fleet_asset_attachments (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID           NOT NULL,
    name                    VARCHAR(150)   NOT NULL,
    category                VARCHAR(50),                 -- e.g. PLOUGH, SLASHER, FORKS, BUCKET
    serial_number           VARCHAR(100),
    description             TEXT,
    purchase_date           DATE,
    purchase_price          NUMERIC(15, 2),
    status                  VARCHAR(10)    NOT NULL,     -- AVAILABLE | ATTACHED | RETIRED
    current_vehicle_id      UUID           REFERENCES fleet_vehicles (id),
    attached_at             TIMESTAMPTZ,
    attached_engine_hours   NUMERIC(10, 1),
    accumulated_hours       NUMERIC(10, 1) NOT NULL DEFAULT 0,   -- hours in use from closed assignments
    notes                   TEXT,
    created_at              TIMESTAMPTZ    NOT NULL,
    updated_at              TIMESTAMPTZ    NOT NULL,
    version                 BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT chk_fleet_asset_attachments_attached CHECK (
        (status = 'ATTACHED' AND current_vehicle_id IS NOT NULL AND attached_engine_hours IS NOT NULL)
        OR (status <> 'ATTACHED' AND current_vehicle_id IS NULL))
);
CREATE UNIQUE INDEX uq_fleet_asset_attachments_serial ON fleet_asset_attachments (tenant_id, LOWER(serial_number))
    WHERE serial_number IS NOT NULL;
CREATE INDEX idx_fleet_asset_attachments_tenant ON fleet_asset_attachments (tenant_id, status);
CREATE INDEX idx_fleet_asset_attachments_vehicle ON fleet_asset_attachments (current_vehicle_id) WHERE current_vehicle_id IS NOT NULL;

CREATE TABLE fleet_attachment_assignments (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID           NOT NULL,
    attachment_id         UUID           NOT NULL REFERENCES fleet_asset_attachments (id),
    vehicle_id            UUID           NOT NULL REFERENCES fleet_vehicles (id),
    attached_at           TIMESTAMPTZ    NOT NULL,
    attached_engine_hours NUMERIC(10, 1) NOT NULL,
    detached_at           TIMESTAMPTZ,
    detached_engine_hours NUMERIC(10, 1)
);
CREATE UNIQUE INDEX uq_fleet_attachment_assignments_open ON fleet_attachment_assignments (attachment_id) WHERE detached_at IS NULL;
CREATE INDEX idx_fleet_attachment_assignments_attachment ON fleet_attachment_assignments (tenant_id, attachment_id, attached_at DESC);