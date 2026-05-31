-- V16__property_module.sql

-- Properties: buildings or land parcels
CREATE TABLE properties (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    -- WHY customer_id? Property may be managed on behalf of a CRM customer (owner)
    customer_id     UUID,
    name            VARCHAR(255) NOT NULL,
    property_type   VARCHAR(50)  NOT NULL,
    address         JSONB        NOT NULL,
    description     TEXT,
    purchase_price  NUMERIC(15,2),
    purchase_date   DATE,
    market_value    NUMERIC(15,2),
    photo_url       VARCHAR(500),
    notes           TEXT,
    active          BOOLEAN     NOT NULL DEFAULT true,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMP,
    deleted_by      UUID,
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_properties PRIMARY KEY (id),
    CONSTRAINT chk_property_type CHECK (
        property_type IN (
            'RESIDENTIAL','COMMERCIAL','INDUSTRIAL',
            'RETAIL','MIXED_USE','LAND','OTHER'
        )
    )
);

-- Units: rentable spaces within a property
CREATE TABLE property_units (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    property_id     UUID        NOT NULL,
    unit_number     VARCHAR(50)  NOT NULL,   -- "1A", "Shop 3", "Flat 12B"
    unit_type       VARCHAR(50)  NOT NULL,   -- STUDIO, 1BED, 2BED, 3BED, COMMERCIAL, PARKING
    floor_number    INTEGER,
    size_sqm        NUMERIC(10,2),
    -- WHY store base_rent here? Default rent for new leases. Can be overridden per lease.
    base_rent       NUMERIC(15,2) NOT NULL,
    deposit_amount  NUMERIC(15,2),
    status          VARCHAR(20)  NOT NULL DEFAULT 'VACANT',
    furnished       BOOLEAN     NOT NULL DEFAULT false,
    amenities       JSONB,      -- ["parking", "garden", "pool", "wifi"]
    notes           TEXT,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMP,
    deleted_by      UUID,
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_property_units PRIMARY KEY (id),
    CONSTRAINT fk_unit_property
        FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE CASCADE,
    CONSTRAINT uq_unit_number_per_property
        UNIQUE (property_id, unit_number),
    CONSTRAINT chk_unit_type CHECK (
        unit_type IN (
            'STUDIO','1BED','2BED','3BED','4BED','PENTHOUSE',
            'COMMERCIAL','RETAIL','WAREHOUSE','PARKING','OTHER'
        )
    ),
    CONSTRAINT chk_unit_status CHECK (
        status IN ('VACANT','OCCUPIED','MAINTENANCE','RESERVED')
    )
);

-- Leases: rental agreements between landlord and tenant
CREATE TABLE leases (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,       -- HandyFlow tenant (landlord)
    unit_id         UUID        NOT NULL,
    -- WHY customer_id? The renter is a CRM customer
    customer_id     UUID,
    -- Lessee details stored directly for documents (CRM may change)
    lessee_name     VARCHAR(255) NOT NULL,
    lessee_id_number VARCHAR(13),
    lessee_email    VARCHAR(255),
    lessee_phone    VARCHAR(20),
    start_date      DATE        NOT NULL,
    end_date        DATE,       -- NULL = month-to-month
    monthly_rent    NUMERIC(15,2) NOT NULL,
    deposit_amount  NUMERIC(15,2) NOT NULL DEFAULT 0,
    deposit_paid    BOOLEAN     NOT NULL DEFAULT false,
    -- WHY payment_day? Rent is due on a specific day each month (e.g. 1st or 25th)
    payment_day     INTEGER     NOT NULL DEFAULT 1,
    escalation_rate NUMERIC(5,2) DEFAULT 0,  -- annual % increase
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes           TEXT,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMP,
    deleted_by      UUID,
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_leases PRIMARY KEY (id),
    CONSTRAINT fk_lease_unit
        FOREIGN KEY (unit_id) REFERENCES property_units(id),
    CONSTRAINT chk_lease_status CHECK (
        status IN ('ACTIVE','EXPIRED','TERMINATED','PENDING')
    ),
    CONSTRAINT chk_payment_day CHECK (payment_day BETWEEN 1 AND 31),
    CONSTRAINT chk_lease_dates CHECK (
        end_date IS NULL OR end_date > start_date
    )
);

-- Lease payments: monthly rent tracking
CREATE TABLE lease_payments (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    lease_id        UUID        NOT NULL,
    -- WHY period_year/month? Identifies which month the payment is for
    period_year     INTEGER     NOT NULL,
    period_month    INTEGER     NOT NULL,   -- 1-12
    amount_due      NUMERIC(15,2) NOT NULL,
    amount_paid     NUMERIC(15,2) NOT NULL DEFAULT 0,
    due_date        DATE        NOT NULL,
    paid_date       DATE,
    payment_method  VARCHAR(50),  -- EFT, CASH, CARD, DEBIT_ORDER
    reference       VARCHAR(100),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    notes           TEXT,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT pk_lease_payments PRIMARY KEY (id),
    CONSTRAINT fk_payment_lease
        FOREIGN KEY (lease_id) REFERENCES leases(id),
    CONSTRAINT uq_payment_period
        UNIQUE (lease_id, period_year, period_month),
    CONSTRAINT chk_payment_status CHECK (
        status IN ('PENDING','PARTIAL','PAID','OVERDUE','WAIVED')
    ),
    CONSTRAINT chk_period_month CHECK (period_month BETWEEN 1 AND 12)
);

-- Inspections: move-in and move-out condition reports
CREATE TABLE property_inspections (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    unit_id         UUID        NOT NULL,
    lease_id        UUID,
    type            VARCHAR(20) NOT NULL,  -- MOVE_IN, MOVE_OUT, ROUTINE, MAINTENANCE
    inspected_at    TIMESTAMP   NOT NULL,
    inspected_by    VARCHAR(100),
    overall_condition VARCHAR(20) DEFAULT 'GOOD',  -- EXCELLENT, GOOD, FAIR, POOR
    notes           TEXT,
    -- WHY JSONB? Flexible room-by-room condition items
    items           JSONB,
    photo_urls      JSONB,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT pk_property_inspections PRIMARY KEY (id),
    CONSTRAINT fk_inspection_unit
        FOREIGN KEY (unit_id) REFERENCES property_units(id),
    CONSTRAINT fk_inspection_lease
        FOREIGN KEY (lease_id) REFERENCES leases(id) ON DELETE SET NULL,
    CONSTRAINT chk_inspection_type CHECK (
        type IN ('MOVE_IN','MOVE_OUT','ROUTINE','MAINTENANCE')
    ),
    CONSTRAINT chk_inspection_condition CHECK (
        overall_condition IN ('EXCELLENT','GOOD','FAIR','POOR')
    )
);

CREATE INDEX idx_properties_tenant     ON properties(tenant_id)           WHERE deleted_at IS NULL;
CREATE INDEX idx_units_property        ON property_units(property_id)     WHERE deleted_at IS NULL;
CREATE INDEX idx_units_status          ON property_units(tenant_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_leases_unit           ON leases(unit_id)                 WHERE deleted_at IS NULL;
CREATE INDEX idx_leases_customer       ON leases(customer_id)             WHERE deleted_at IS NULL;
CREATE INDEX idx_leases_status         ON leases(tenant_id, status)       WHERE deleted_at IS NULL;
CREATE INDEX idx_payments_lease        ON lease_payments(lease_id, period_year, period_month);
CREATE INDEX idx_payments_overdue      ON lease_payments(tenant_id, status, due_date)
    WHERE status IN ('PENDING','OVERDUE','PARTIAL');
CREATE INDEX idx_inspections_unit      ON property_inspections(unit_id, inspected_at);