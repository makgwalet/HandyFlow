-- Core domain: bookable resources, offerings, and actual bookings.
-- Second migration for the Booking Agency module — run after
-- V_create_booking_agency_tables.sql (practice shell + client
-- portfolio).

CREATE TABLE booka_resources (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL,
    client_id             UUID NOT NULL REFERENCES booka_agency_clients(id),
    name                  VARCHAR(255) NOT NULL,
    role_description      VARCHAR(255),
    working_hours_start   TIME,
    working_hours_end     TIME,
    active                BOOLEAN NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_booka_resources_client ON booka_resources (client_id);

CREATE TABLE booka_offerings (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID NOT NULL,
    client_id          UUID NOT NULL REFERENCES booka_agency_clients(id),
    name               VARCHAR(255) NOT NULL,
    duration_minutes   INTEGER NOT NULL,
    buffer_minutes     INTEGER NOT NULL DEFAULT 0,
    price              NUMERIC(10,2),
    active             BOOLEAN NOT NULL DEFAULT true,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_booka_offerings_client ON booka_offerings (client_id);

CREATE TABLE booka_bookings (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL,
    client_id        UUID NOT NULL REFERENCES booka_agency_clients(id),
    resource_id      UUID NOT NULL REFERENCES booka_resources(id),
    offering_id      UUID NOT NULL REFERENCES booka_offerings(id),
    booking_number   VARCHAR(30) NOT NULL,
    customer_name    VARCHAR(255) NOT NULL,
    customer_phone   VARCHAR(50),
    customer_email   VARCHAR(255),
    start_datetime   TIMESTAMP NOT NULL,
    end_datetime     TIMESTAMP NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    notes            TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_booka_bookings_resource_time ON booka_bookings (resource_id, start_datetime, end_datetime);
CREATE INDEX idx_booka_bookings_client ON booka_bookings (client_id);
-- The resource_id + time-range index above is the one this layer's
-- conflict check actually depends on for reasonable performance —
-- added deliberately, not an afterthought.