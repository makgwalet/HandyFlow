-- Foundation tables for the Booking Agency module — practice shell and
-- client portfolio only. Bookable resources (staff/services per
-- client), the slot/booking engine, and billing are separate, later
-- migrations, matching how Payroll Bureau and Recruitment Agency were
-- both built layer by layer.

CREATE TABLE booka_agency_profiles (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL UNIQUE,
    agency_name           VARCHAR(255) NOT NULL,
    registration_number   VARCHAR(100),
    email                 VARCHAR(255),
    phone                 VARCHAR(50),
    physical_address      TEXT,
    logo_url              VARCHAR(500),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE booka_agency_clients (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    trading_name      VARCHAR(255) NOT NULL,
    business_type     VARCHAR(100),
    timezone          VARCHAR(50) NOT NULL DEFAULT 'Africa/Johannesburg',
    contact_name      VARCHAR(255),
    contact_email     VARCHAR(255),
    contact_phone     VARCHAR(50),
    onboarded_at      DATE,
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes             TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ,
    version           BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_booka_clients_tenant ON booka_agency_clients (tenant_id) WHERE deleted_at IS NULL;