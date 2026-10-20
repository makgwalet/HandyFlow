-- Rename to the next free Flyway version, e.g. V130__fleet_tyres_batteries.sql

-- ── Tyres ──────────────────────────────────────────────────────────────────
-- One row per physical tyre for its whole life; fitments record where it has been.
CREATE TABLE fleet_tyres (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID           NOT NULL,
    serial_number         VARCHAR(100)   NOT NULL,
    brand                 VARCHAR(100)   NOT NULL,
    pattern               VARCHAR(100),
    size                  VARCHAR(30)    NOT NULL,     -- e.g. 295/80R22.5
    purchase_date         DATE,
    purchase_price        NUMERIC(15, 2),
    supplier              VARCHAR(255),
    initial_tread_depth_mm NUMERIC(4, 1),              -- tread when new / after the latest retread
    retread_count         INTEGER        NOT NULL DEFAULT 0,
    status                VARCHAR(20)    NOT NULL,     -- IN_STOCK | FITTED | AWAITING_RETREAD | SCRAPPED
    current_vehicle_id    UUID           REFERENCES fleet_vehicles (id),
    current_position      VARCHAR(10),
    fitted_at             TIMESTAMPTZ,
    fitted_odometer       INTEGER,
    accumulated_km        INTEGER        NOT NULL DEFAULT 0,   -- km from closed fitments
    last_tread_depth_mm   NUMERIC(4, 1),
    last_measured_at      TIMESTAMPTZ,
    notes                 TEXT,
    scrapped_at           TIMESTAMPTZ,
    scrap_reason          VARCHAR(500),
    created_at            TIMESTAMPTZ    NOT NULL,
    updated_at            TIMESTAMPTZ    NOT NULL,
    version               BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT chk_fleet_tyres_fitted CHECK (
        (status = 'FITTED' AND current_vehicle_id IS NOT NULL AND current_position IS NOT NULL)
        OR (status <> 'FITTED' AND current_vehicle_id IS NULL AND current_position IS NULL))
);
CREATE UNIQUE INDEX uq_fleet_tyres_serial ON fleet_tyres (tenant_id, LOWER(serial_number));
-- One tyre per wheel position on a vehicle (any number of spares).
CREATE UNIQUE INDEX uq_fleet_tyres_position ON fleet_tyres (current_vehicle_id, current_position)
    WHERE status = 'FITTED' AND current_position NOT LIKE 'SPARE%';
CREATE INDEX idx_fleet_tyres_tenant_status ON fleet_tyres (tenant_id, status);
CREATE INDEX idx_fleet_tyres_vehicle ON fleet_tyres (tenant_id, current_vehicle_id) WHERE current_vehicle_id IS NOT NULL;

CREATE TABLE fleet_tyre_fitments (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID         NOT NULL,
    tyre_id               UUID         NOT NULL REFERENCES fleet_tyres (id),
    vehicle_id            UUID         NOT NULL REFERENCES fleet_vehicles (id),
    position              VARCHAR(10)  NOT NULL,
    fitted_at             TIMESTAMPTZ  NOT NULL,
    fitted_odometer       INTEGER      NOT NULL,
    removed_at            TIMESTAMPTZ,
    removed_odometer      INTEGER,
    removal_reason        VARCHAR(30),
    fit_work_order_id     UUID         REFERENCES fleet_work_orders (id),
    remove_work_order_id  UUID         REFERENCES fleet_work_orders (id)
);
CREATE UNIQUE INDEX uq_fleet_tyre_fitments_open ON fleet_tyre_fitments (tyre_id) WHERE removed_at IS NULL;
CREATE INDEX idx_fleet_tyre_fitments_tyre ON fleet_tyre_fitments (tenant_id, tyre_id, fitted_at DESC);

CREATE TABLE fleet_tyre_measurements (
    id             UUID PRIMARY KEY,
    tenant_id      UUID           NOT NULL,
    tyre_id        UUID           NOT NULL REFERENCES fleet_tyres (id),
    vehicle_id     UUID,
    measured_at    TIMESTAMPTZ    NOT NULL,
    tread_depth_mm NUMERIC(4, 1)  NOT NULL,
    pressure_kpa   INTEGER,
    odometer       INTEGER,
    notes          VARCHAR(500),
    measured_by    UUID
);
CREATE INDEX idx_fleet_tyre_measurements_tyre ON fleet_tyre_measurements (tenant_id, tyre_id, measured_at DESC);

CREATE TABLE fleet_tyre_retreads (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID           NOT NULL,
    tyre_id               UUID           NOT NULL REFERENCES fleet_tyres (id),
    retreaded_at          DATE           NOT NULL,
    supplier              VARCHAR(255),
    cost                  NUMERIC(15, 2),
    new_tread_depth_mm    NUMERIC(4, 1)  NOT NULL,
    notes                 VARCHAR(500),
    recorded_by           UUID
);
CREATE INDEX idx_fleet_tyre_retreads_tyre ON fleet_tyre_retreads (tenant_id, tyre_id, retreaded_at DESC);

-- ── Batteries ──────────────────────────────────────────────────────────────
CREATE TABLE fleet_batteries (
    id                     UUID PRIMARY KEY,
    tenant_id              UUID           NOT NULL,
    serial_number          VARCHAR(100)   NOT NULL,
    brand                  VARCHAR(100)   NOT NULL,
    model                  VARCHAR(100),
    nominal_voltage        INTEGER        NOT NULL,   -- 12 or 24
    capacity_ah            INTEGER,
    supplier               VARCHAR(255),
    purchase_date          DATE,
    purchase_price         NUMERIC(15, 2),
    warranty_expiry        DATE,
    status                 VARCHAR(20)    NOT NULL,   -- IN_STOCK | INSTALLED | FAULTY | SCRAPPED
    current_vehicle_id     UUID           REFERENCES fleet_vehicles (id),
    current_position       VARCHAR(10),
    installed_at           TIMESTAMPTZ,
    installed_odometer     INTEGER,
    days_in_service        INTEGER        NOT NULL DEFAULT 0,   -- from closed installations
    last_voltage           NUMERIC(4, 2),
    last_tested_at         TIMESTAMPTZ,
    last_test_result       VARCHAR(10),
    notes                  TEXT,
    scrapped_at            TIMESTAMPTZ,
    scrap_reason           VARCHAR(500),
    created_at             TIMESTAMPTZ    NOT NULL,
    updated_at             TIMESTAMPTZ    NOT NULL,
    version                BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT chk_fleet_batteries_voltage CHECK (nominal_voltage IN (12, 24)),
    CONSTRAINT chk_fleet_batteries_installed CHECK (
        (status = 'INSTALLED' AND current_vehicle_id IS NOT NULL AND current_position IS NOT NULL)
        OR (status <> 'INSTALLED' AND current_vehicle_id IS NULL AND current_position IS NULL))
);
CREATE UNIQUE INDEX uq_fleet_batteries_serial ON fleet_batteries (tenant_id, LOWER(serial_number));
CREATE UNIQUE INDEX uq_fleet_batteries_position ON fleet_batteries (current_vehicle_id, current_position)
    WHERE status = 'INSTALLED';
CREATE INDEX idx_fleet_batteries_tenant_status ON fleet_batteries (tenant_id, status);
CREATE INDEX idx_fleet_batteries_warranty ON fleet_batteries (warranty_expiry) WHERE status = 'INSTALLED' AND warranty_expiry IS NOT NULL;

CREATE TABLE fleet_battery_installations (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID         NOT NULL,
    battery_id            UUID         NOT NULL REFERENCES fleet_batteries (id),
    vehicle_id            UUID         NOT NULL REFERENCES fleet_vehicles (id),
    position              VARCHAR(10)  NOT NULL,
    installed_at          TIMESTAMPTZ  NOT NULL,
    installed_odometer    INTEGER      NOT NULL,
    removed_at            TIMESTAMPTZ,
    removed_odometer      INTEGER,
    removal_reason        VARCHAR(30),
    install_work_order_id UUID         REFERENCES fleet_work_orders (id),
    remove_work_order_id  UUID         REFERENCES fleet_work_orders (id)
);
CREATE UNIQUE INDEX uq_fleet_battery_installations_open ON fleet_battery_installations (battery_id) WHERE removed_at IS NULL;
CREATE INDEX idx_fleet_battery_installations_battery ON fleet_battery_installations (tenant_id, battery_id, installed_at DESC);

CREATE TABLE fleet_battery_tests (
    id          UUID PRIMARY KEY,
    tenant_id   UUID          NOT NULL,
    battery_id  UUID          NOT NULL REFERENCES fleet_batteries (id),
    vehicle_id  UUID,
    tested_at   TIMESTAMPTZ   NOT NULL,
    voltage     NUMERIC(4, 2) NOT NULL,
    result      VARCHAR(10)   NOT NULL,   -- GOOD | WEAK | FAILED (derived from the voltage)
    notes       VARCHAR(500),
    tested_by   UUID
);
CREATE INDEX idx_fleet_battery_tests_battery ON fleet_battery_tests (tenant_id, battery_id, tested_at DESC);