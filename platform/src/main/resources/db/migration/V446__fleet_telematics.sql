-- Rename to the next free Flyway version, e.g. V132__fleet_telematics.sql

-- ── Ingest authentication ──────────────────────────────────────────────────
-- Providers authenticate with a per-tenant API key; only its SHA-256 hash is stored.
CREATE TABLE fleet_telematics_ingest_keys (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    name        VARCHAR(150) NOT NULL,
    key_hash    VARCHAR(64)  NOT NULL UNIQUE,
    key_prefix  VARCHAR(16)  NOT NULL,         -- first characters, for recognising a key in a list
    created_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID,
    last_used_at TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ
);
CREATE INDEX idx_fleet_telematics_keys_tenant ON fleet_telematics_ingest_keys (tenant_id);

-- ── Devices: the provider's unit id → one of our vehicles ───────────────────
CREATE TABLE fleet_telematics_devices (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    provider      VARCHAR(50)  NOT NULL DEFAULT 'GENERIC',
    external_id   VARCHAR(100) NOT NULL,
    vehicle_id    UUID         NOT NULL REFERENCES fleet_vehicles (id),
    name          VARCHAR(150),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    last_seen_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_fleet_telematics_devices_external ON fleet_telematics_devices (tenant_id, LOWER(external_id));
CREATE INDEX idx_fleet_telematics_devices_vehicle ON fleet_telematics_devices (tenant_id, vehicle_id);

-- ── Positions: high volume, insert-only. Duplicates are ignored on (vehicle, time). ──
-- Consider partitioning by month if a tenant reports at high frequency; a nightly job purges
-- rows older than fleet.telematics.position-retention-days (default 90).
CREATE TABLE fleet_telematics_positions (
    id           UUID PRIMARY KEY,
    tenant_id    UUID             NOT NULL,
    vehicle_id   UUID             NOT NULL,
    recorded_at  TIMESTAMPTZ      NOT NULL,
    latitude     DOUBLE PRECISION NOT NULL,
    longitude    DOUBLE PRECISION NOT NULL,
    speed_kph    DOUBLE PRECISION,
    heading      INTEGER,
    odometer_km  DOUBLE PRECISION,
    ignition_on  BOOLEAN,
    received_at  TIMESTAMPTZ      NOT NULL
);
CREATE UNIQUE INDEX uq_fleet_telematics_positions_vehicle_time ON fleet_telematics_positions (vehicle_id, recorded_at);
CREATE INDEX idx_fleet_telematics_positions_recorded ON fleet_telematics_positions (recorded_at);

-- Latest known state per vehicle: the live map, plus the running state the rules need.
CREATE TABLE fleet_vehicle_last_positions (
    vehicle_id             UUID PRIMARY KEY REFERENCES fleet_vehicles (id),
    tenant_id              UUID             NOT NULL,
    latitude               DOUBLE PRECISION NOT NULL,
    longitude              DOUBLE PRECISION NOT NULL,
    speed_kph              DOUBLE PRECISION,
    heading                INTEGER,
    odometer_km            DOUBLE PRECISION,
    ignition_on            BOOLEAN,
    recorded_at            TIMESTAMPTZ      NOT NULL,
    received_at            TIMESTAMPTZ      NOT NULL,
    idle_since             TIMESTAMPTZ,
    idle_event_raised      BOOLEAN          NOT NULL DEFAULT FALSE,
    last_speeding_event_at TIMESTAMPTZ
);
CREATE INDEX idx_fleet_last_positions_tenant ON fleet_vehicle_last_positions (tenant_id);

-- ── Geofences ──────────────────────────────────────────────────────────────
CREATE TABLE fleet_geofences (
    id                UUID PRIMARY KEY,
    tenant_id         UUID             NOT NULL,
    name              VARCHAR(150)     NOT NULL,
    shape             VARCHAR(10)      NOT NULL,       -- CIRCLE | POLYGON
    center_latitude   DOUBLE PRECISION,
    center_longitude  DOUBLE PRECISION,
    radius_meters     INTEGER,
    polygon_json      TEXT,                            -- [[lat,lng],[lat,lng],...]
    alert_on_enter    BOOLEAN          NOT NULL DEFAULT FALSE,
    alert_on_exit     BOOLEAN          NOT NULL DEFAULT FALSE,
    speed_limit_kph   INTEGER,                         -- optional lower limit inside this zone
    active            BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ      NOT NULL,
    updated_at        TIMESTAMPTZ      NOT NULL,
    deleted_at        TIMESTAMPTZ,
    version           BIGINT           NOT NULL DEFAULT 0,
    CONSTRAINT chk_fleet_geofences_shape CHECK (
        (shape = 'CIRCLE' AND center_latitude IS NOT NULL AND center_longitude IS NOT NULL AND radius_meters IS NOT NULL)
        OR (shape = 'POLYGON' AND polygon_json IS NOT NULL))
);
CREATE INDEX idx_fleet_geofences_tenant ON fleet_geofences (tenant_id) WHERE deleted_at IS NULL;

-- Whether each vehicle was inside each geofence at its last evaluated position — so transitions can be detected.
CREATE TABLE fleet_geofence_states (
    id          UUID PRIMARY KEY,
    vehicle_id  UUID        NOT NULL REFERENCES fleet_vehicles (id),
    geofence_id UUID        NOT NULL REFERENCES fleet_geofences (id) ON DELETE CASCADE,
    inside      BOOLEAN     NOT NULL,
    since       TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_fleet_geofence_states UNIQUE (vehicle_id, geofence_id)
);

-- ── Events (derived by our rules, or sent natively by the provider) ─────────
CREATE TABLE fleet_telematics_events (
    id               UUID PRIMARY KEY,
    tenant_id        UUID             NOT NULL,
    vehicle_id       UUID             NOT NULL REFERENCES fleet_vehicles (id),
    driver_id        UUID,
    trip_id          UUID,
    event_type       VARCHAR(30)      NOT NULL,
    severity         VARCHAR(10)      NOT NULL,
    occurred_at      TIMESTAMPTZ      NOT NULL,
    latitude         DOUBLE PRECISION,
    longitude        DOUBLE PRECISION,
    event_value      DOUBLE PRECISION,               -- e.g. speed in km/h
    limit_value      DOUBLE PRECISION,               -- e.g. the speed limit that applied
    duration_seconds INTEGER,
    details          VARCHAR(500),
    geofence_id      UUID,
    source           VARCHAR(10)      NOT NULL,      -- DERIVED | PROVIDER
    acknowledged_at  TIMESTAMPTZ,
    acknowledged_by  UUID,
    created_at       TIMESTAMPTZ      NOT NULL
);
CREATE UNIQUE INDEX uq_fleet_telematics_events_dedupe ON fleet_telematics_events (vehicle_id, event_type, occurred_at);
CREATE INDEX idx_fleet_telematics_events_tenant ON fleet_telematics_events (tenant_id, occurred_at DESC);
CREATE INDEX idx_fleet_telematics_events_driver ON fleet_telematics_events (tenant_id, driver_id, occurred_at) WHERE driver_id IS NOT NULL;

-- ── Rule settings (one row per tenant; absent row = built-in defaults) ──────
CREATE TABLE fleet_telematics_settings (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID        NOT NULL UNIQUE,
    default_speed_limit_kph     INTEGER     NOT NULL DEFAULT 120,
    speeding_tolerance_kph      INTEGER     NOT NULL DEFAULT 5,
    speeding_cooldown_minutes   INTEGER     NOT NULL DEFAULT 5,
    alert_overspeed_kph         INTEGER     NOT NULL DEFAULT 30,
    idling_threshold_minutes    INTEGER     NOT NULL DEFAULT 10,
    offline_after_minutes       INTEGER     NOT NULL DEFAULT 30,
    updated_at                  TIMESTAMPTZ NOT NULL,
    updated_by                  UUID,
    version                     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT chk_fleet_telematics_settings CHECK (
        default_speed_limit_kph BETWEEN 20 AND 200 AND speeding_tolerance_kph BETWEEN 0 AND 30
        AND speeding_cooldown_minutes BETWEEN 1 AND 120 AND alert_overspeed_kph BETWEEN 5 AND 100
        AND idling_threshold_minutes BETWEEN 1 AND 240 AND offline_after_minutes BETWEEN 5 AND 1440)
);