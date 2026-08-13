-- Foundation tables for the Recruitment Agency module — practice shell
-- and client portfolio only. Job requisitions, candidate pipeline,
-- interviews, placement billing, and client portal are separate, later
-- migrations, matching how Payroll Bureau was built layer by layer.

CREATE TABLE reca_agency_profiles (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL UNIQUE,
    agency_name                 VARCHAR(255) NOT NULL,
    registration_number         VARCHAR(100),
    email                       VARCHAR(255),
    phone                       VARCHAR(50),
    physical_address            TEXT,
    logo_url                    VARCHAR(500),
    default_placement_fee_pct   NUMERIC(5,2) NOT NULL DEFAULT 15.00,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reca_agency_clients (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    trading_name            VARCHAR(255) NOT NULL,
    registration_number     VARCHAR(100),
    industry                VARCHAR(100),
    placement_fee_pct       NUMERIC(5,2), -- null = use agency default
    guarantee_period_days   INTEGER,
    contact_name            VARCHAR(255),
    contact_email           VARCHAR(255),
    contact_phone           VARCHAR(50),
    onboarded_at            DATE,
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes                   TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at               TIMESTAMPTZ,
    version                  BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_reca_clients_tenant ON reca_agency_clients (tenant_id) WHERE deleted_at IS NULL;