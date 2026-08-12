-- Foundation tables for the HR/Payroll Bureau module — practice shell
-- and client portfolio only. Payroll core (employees/pay runs/payslips),
-- SARS deadlines, workpapers, and billing are separate, later migrations
-- as those layers get built.

CREATE TABLE pay_bureau_profiles (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL UNIQUE,
    firm_name            VARCHAR(255) NOT NULL,
    registration_number  VARCHAR(100),
    sdl_number           VARCHAR(50),
    email                VARCHAR(255),
    phone                VARCHAR(50),
    physical_address     TEXT,
    logo_url             VARCHAR(500),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE pay_clients (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL,
    trading_name         VARCHAR(255) NOT NULL,
    registration_number  VARCHAR(100),
    paye_reference       VARCHAR(50),
    uif_reference        VARCHAR(50),
    sdl_reference        VARCHAR(50),
    pay_frequency        VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    pay_day              INTEGER,
    contact_name         VARCHAR(255),
    contact_email        VARCHAR(255),
    contact_phone        VARCHAR(50),
    onboarded_at         DATE,
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes                TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at           TIMESTAMPTZ,
    version              BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_pay_clients_tenant ON pay_clients (tenant_id) WHERE deleted_at IS NULL;