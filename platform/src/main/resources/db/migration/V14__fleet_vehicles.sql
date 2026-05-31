-- Fleet vehicles per tenant
CREATE TABLE fleet_vehicles (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL,
    customer_id         UUID,
    registration        VARCHAR(20)  NOT NULL,
    make                VARCHAR(100) NOT NULL,  -- Toyota, Ford, Mercedes
    model               VARCHAR(100) NOT NULL,  -- Hilux, Ranger, Sprinter
    year                INTEGER,
    colour              VARCHAR(50),
    vin                 VARCHAR(17),            -- Vehicle Identification Number
    vehicle_type        VARCHAR(50)  NOT NULL,  -- BAKKIE, SEDAN, SUV, TRUCK, VAN, BUS, MOTORCYCLE
    status              VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    -- Licence and compliance
    licence_disc_expiry DATE,
    roadworthy_expiry   DATE,
    insurance_expiry    DATE,
    -- Service tracking (odometer-based)
    current_odometer    INTEGER      NOT NULL DEFAULT 0,  -- km
    last_service_km     INTEGER      NOT NULL DEFAULT 0,
    service_interval_km INTEGER      NOT NULL DEFAULT 15000,
    -- Fuel
    fuel_type           VARCHAR(20)  NOT NULL DEFAULT 'DIESEL',  -- DIESEL, PETROL, ELECTRIC, HYBRID
    tank_capacity_litres NUMERIC(8,2),
    -- Costs
    purchase_date       DATE,
    purchase_price      NUMERIC(15,2),
    daily_rate          NUMERIC(15,2),
    notes               TEXT,
    photo_url           VARCHAR(500),
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMP,
    deleted_by          UUID,
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_fleet_vehicles PRIMARY KEY (id),
    CONSTRAINT uq_vehicle_registration UNIQUE (tenant_id, registration),
    CONSTRAINT chk_vehicle_status CHECK (
        status IN ('AVAILABLE','IN_USE','SERVICE','BREAKDOWN','RETIRED')
    ),
    CONSTRAINT chk_vehicle_type CHECK (
        vehicle_type IN ('BAKKIE','SEDAN','SUV','TRUCK','VAN','BUS','MOTORCYCLE','OTHER')
    ),
    CONSTRAINT chk_fuel_type CHECK (
        fuel_type IN ('DIESEL','PETROL','ELECTRIC','HYBRID','GAS')
    )
);

-- Vehicle service records
CREATE TABLE fleet_services (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    vehicle_id      UUID        NOT NULL,
    type            VARCHAR(50) NOT NULL,  -- SERVICE, REPAIR, TYRE, LICENCE, ROADWORTHY, INSURANCE
    description     TEXT        NOT NULL,
    odometer_at_service INTEGER,
    next_service_km INTEGER,
    service_date    DATE        NOT NULL,
    cost            NUMERIC(15,2),
    supplier        VARCHAR(255),
    invoice_ref     VARCHAR(100),
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMP,
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_fleet_services PRIMARY KEY (id),
    CONSTRAINT fk_service_vehicle
        FOREIGN KEY (vehicle_id) REFERENCES fleet_vehicles(id),
    CONSTRAINT chk_service_type CHECK (
        type IN ('SERVICE','REPAIR','TYRE','LICENCE','ROADWORTHY','INSURANCE','OTHER')
    )
);

-- Trip logs: who drove which vehicle, how far
CREATE TABLE fleet_trips (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    vehicle_id      UUID        NOT NULL,
    guard_id        UUID,           -- reuse security module driver if available
    driver_name     VARCHAR(200),
    purpose         TEXT,
    start_location  VARCHAR(255),
    end_location    VARCHAR(255),
    start_odometer  INTEGER     NOT NULL,
    end_odometer    INTEGER,
    start_at        TIMESTAMP   NOT NULL,
    end_at          TIMESTAMP,
    fuel_used_litres NUMERIC(8,2),
    notes           TEXT,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT pk_fleet_trips PRIMARY KEY (id),
    CONSTRAINT fk_trip_vehicle
        FOREIGN KEY (vehicle_id) REFERENCES fleet_vehicles(id)
);

CREATE INDEX idx_fleet_vehicles_tenant  ON fleet_vehicles(tenant_id)          WHERE deleted_at IS NULL;
CREATE INDEX idx_fleet_vehicles_status  ON fleet_vehicles(tenant_id, status)   WHERE deleted_at IS NULL;
CREATE INDEX idx_fleet_services_vehicle ON fleet_services(vehicle_id, service_date) WHERE deleted_at IS NULL;
CREATE INDEX idx_fleet_trips_vehicle    ON fleet_trips(vehicle_id, start_at);

-- WHY this index? Daily alerts for expiring licences/roadworthy
CREATE INDEX idx_fleet_expiry ON fleet_vehicles(tenant_id, licence_disc_expiry)
    WHERE deleted_at IS NULL AND licence_disc_expiry IS NOT NULL;