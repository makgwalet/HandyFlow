-- V15__fuel_logistics.sql

-- Fuel tanks: physical storage at your depot(s)
CREATE TABLE fuel_tanks (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    name            VARCHAR(100) NOT NULL,   -- "Main Tank A", "Diesel Tank 1"
    fuel_type       VARCHAR(20) NOT NULL,    -- DIESEL, PETROL, PARAFFIN, GAS
    capacity_litres NUMERIC(12,2) NOT NULL,
    current_litres  NUMERIC(12,2) NOT NULL DEFAULT 0,
    location        VARCHAR(255),
    notes           TEXT,
    active          BOOLEAN     NOT NULL DEFAULT true,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMP,
    deleted_by      UUID,
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_fuel_tanks PRIMARY KEY (id),
    CONSTRAINT chk_tank_fuel_type CHECK (
        fuel_type IN ('DIESEL','PETROL','PARAFFIN','GAS','OTHER')
    ),
    CONSTRAINT chk_tank_capacity CHECK (capacity_litres > 0),
    CONSTRAINT chk_tank_level CHECK (current_litres >= 0)
);

-- Fuel suppliers
CREATE TABLE fuel_suppliers (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    name            VARCHAR(255) NOT NULL,
    contact_name    VARCHAR(100),
    contact_phone   VARCHAR(20),
    contact_email   VARCHAR(255),
    account_number  VARCHAR(50),
    notes           TEXT,
    active          BOOLEAN     NOT NULL DEFAULT true,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMP,
    deleted_by      UUID,
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_fuel_suppliers PRIMARY KEY (id)
);

-- Fuel receipts: stock arriving from supplier
CREATE TABLE fuel_receipts (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    tank_id         UUID        NOT NULL,
    supplier_id     UUID,
    litres_received NUMERIC(12,2) NOT NULL,
    price_per_litre NUMERIC(10,4) NOT NULL,
    total_cost      NUMERIC(15,2) NOT NULL,
    received_at     TIMESTAMP   NOT NULL,
    delivery_note   VARCHAR(100),
    invoice_ref     VARCHAR(100),
    -- Tank level before and after — WHY? For reconciliation audit trail
    level_before    NUMERIC(12,2),
    level_after     NUMERIC(12,2),
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMP,
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_fuel_receipts PRIMARY KEY (id),
    CONSTRAINT fk_receipt_tank
        FOREIGN KEY (tank_id) REFERENCES fuel_tanks(id),
    CONSTRAINT fk_receipt_supplier
        FOREIGN KEY (supplier_id) REFERENCES fuel_suppliers(id) ON DELETE SET NULL,
    CONSTRAINT chk_receipt_litres CHECK (litres_received > 0)
);

-- Fuel dispatches: fuel issued to vehicles, machines, or customers
CREATE TABLE fuel_dispatches (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    tank_id         UUID        NOT NULL,
    -- WHY nullable FKs? Fuel can go to: vehicle, earthmoving asset, or external customer
    vehicle_id      UUID,           -- fleet vehicle
    asset_id        UUID,           -- earthmoving machine
    customer_id     UUID,           -- CRM customer (external delivery)
    recipient_name  VARCHAR(255),   -- free-text fallback
    litres_dispensed NUMERIC(12,2) NOT NULL,
    price_per_litre NUMERIC(10,4),
    dispatched_at   TIMESTAMP   NOT NULL,
    odometer_reading INTEGER,       -- for vehicle fuel logs
    hours_reading   NUMERIC(10,1),  -- for machine fuel logs
    authorised_by   VARCHAR(100),
    notes           TEXT,
    level_before    NUMERIC(12,2),
    level_after     NUMERIC(12,2),
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMP,
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_fuel_dispatches PRIMARY KEY (id),
    CONSTRAINT fk_dispatch_tank
        FOREIGN KEY (tank_id) REFERENCES fuel_tanks(id),
    CONSTRAINT chk_dispatch_litres CHECK (litres_dispensed > 0)
);

-- Dip readings: physical tank measurement for reconciliation
CREATE TABLE fuel_dip_readings (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    tank_id         UUID        NOT NULL,
    read_at         TIMESTAMP   NOT NULL,
    actual_litres   NUMERIC(12,2) NOT NULL,
    -- WHY store calculated? Variance = calculated - actual
    -- Negative variance = fuel missing (theft/leak)
    -- Positive variance = measurement error
    calculated_litres NUMERIC(12,2),
    variance_litres NUMERIC(12,2),
    read_by         VARCHAR(100),
    notes           TEXT,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT pk_fuel_dip_readings PRIMARY KEY (id),
    CONSTRAINT fk_dip_tank
        FOREIGN KEY (tank_id) REFERENCES fuel_tanks(id)
);

-- Deliveries: fuel delivered to client sites (if you're a fuel distributor)
CREATE TABLE fuel_deliveries (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    tank_id         UUID        NOT NULL,
    customer_id     UUID,
    delivery_address JSONB,
    fuel_type       VARCHAR(20) NOT NULL,
    litres_ordered  NUMERIC(12,2) NOT NULL,
    litres_delivered NUMERIC(12,2),
    price_per_litre NUMERIC(10,4) NOT NULL,
    total_amount    NUMERIC(15,2),
    status          VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    scheduled_at    TIMESTAMP   NOT NULL,
    delivered_at    TIMESTAMP,
    driver_name     VARCHAR(100),
    vehicle_reg     VARCHAR(20),
    notes           TEXT,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMP,
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_fuel_deliveries PRIMARY KEY (id),
    CONSTRAINT fk_delivery_tank
        FOREIGN KEY (tank_id) REFERENCES fuel_tanks(id),
    CONSTRAINT chk_delivery_status CHECK (
        status IN ('SCHEDULED','IN_TRANSIT','DELIVERED','CANCELLED')
    )
);

CREATE INDEX idx_fuel_tanks_tenant     ON fuel_tanks(tenant_id)           WHERE deleted_at IS NULL;
CREATE INDEX idx_fuel_receipts_tank    ON fuel_receipts(tank_id, received_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_fuel_dispatches_tank  ON fuel_dispatches(tank_id, dispatched_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_fuel_dips_tank        ON fuel_dip_readings(tank_id, read_at);
CREATE INDEX idx_fuel_deliveries_tenant ON fuel_deliveries(tenant_id, scheduled_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_fuel_deliveries_status ON fuel_deliveries(tenant_id, status)       WHERE deleted_at IS NULL;