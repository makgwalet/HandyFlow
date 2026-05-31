-- V23__bookings_module.sql
-- WHY? Appointment booking for service businesses.
-- Services define what can be booked, staff define who does it,
-- availability defines when, bookings are the actual appointments.

-- ── Services ───────────────────────────────────────────────────────────────
CREATE TABLE booking_services (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id),
    name             VARCHAR(200) NOT NULL,
    description      TEXT,
    duration_minutes INT NOT NULL DEFAULT 60,
    price            NUMERIC(10,2) NOT NULL DEFAULT 0,
    currency         VARCHAR(3) DEFAULT 'ZAR',
    color            VARCHAR(7) DEFAULT '#0D9488',
    active           BOOLEAN NOT NULL DEFAULT true,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at       TIMESTAMP
);

-- ── Booking staff ──────────────────────────────────────────────────────────
CREATE TABLE booking_staff (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    name        VARCHAR(200) NOT NULL,
    email       VARCHAR(200),
    phone       VARCHAR(30),
    employee_id UUID,                     -- optional link to hr_employees
    active      BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ── Service–staff assignments ──────────────────────────────────────────────
CREATE TABLE booking_service_staff (
    service_id UUID NOT NULL REFERENCES booking_services(id) ON DELETE CASCADE,
    staff_id   UUID NOT NULL REFERENCES booking_staff(id)    ON DELETE CASCADE,
    PRIMARY KEY (service_id, staff_id)
);

-- ── Working hours ──────────────────────────────────────────────────────────
-- WHY day_of_week 0–6? ISO: 1=Mon … 7=Sun is common but Java DayOfWeek
-- uses 1–7. We store 0=Sun … 6=Sat to match most calendar UIs.
CREATE TABLE booking_availability (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    staff_id    UUID REFERENCES booking_staff(id),  -- NULL = whole business
    day_of_week INT NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),
    start_time  TIME NOT NULL,
    end_time    TIME NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT true,
    UNIQUE (tenant_id, staff_id, day_of_week)
);

-- ── Blocked time ───────────────────────────────────────────────────────────
CREATE TABLE booking_blocks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    staff_id    UUID REFERENCES booking_staff(id),
    block_date  DATE NOT NULL,
    start_time  TIME,                     -- NULL = full day
    end_time    TIME,
    reason      VARCHAR(200),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ── Bookings ───────────────────────────────────────────────────────────────
CREATE TABLE bookings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    booking_number      VARCHAR(20) NOT NULL,
    service_id          UUID NOT NULL REFERENCES booking_services(id),
    staff_id            UUID REFERENCES booking_staff(id),
    customer_id         UUID,             -- soft link to crm_customers
    client_name         VARCHAR(200) NOT NULL,
    client_email        VARCHAR(200),
    client_phone        VARCHAR(30),
    booking_date        DATE NOT NULL,
    start_time          TIME NOT NULL,
    end_time            TIME NOT NULL,
    duration_minutes    INT NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','CONFIRMED','IN_PROGRESS',
                          'COMPLETED','CANCELLED','NO_SHOW')),
    price               NUMERIC(10,2),
    currency            VARCHAR(3) DEFAULT 'ZAR',
    invoice_id          UUID,
    notes               TEXT,
    internal_notes      TEXT,
    cancellation_reason TEXT,
    reminder_sent       BOOLEAN NOT NULL DEFAULT false,
    reminder_sent_at    TIMESTAMP,
    confirmed_at        TIMESTAMP,
    completed_at        TIMESTAMP,
    cancelled_at        TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, booking_number)
);

-- ── Indexes ────────────────────────────────────────────────────────────────
CREATE INDEX idx_bookings_tenant   ON bookings(tenant_id, booking_date);
CREATE INDEX idx_bookings_status   ON bookings(tenant_id, status);
CREATE INDEX idx_bookings_staff    ON bookings(staff_id, booking_date);
CREATE INDEX idx_bookings_customer ON bookings(customer_id);
CREATE INDEX idx_booking_services_tenant ON booking_services(tenant_id)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_booking_availability_tenant ON booking_availability(tenant_id);
CREATE INDEX idx_booking_blocks_date ON booking_blocks(tenant_id, block_date);