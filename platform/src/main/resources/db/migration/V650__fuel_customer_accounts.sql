-- Fuel Stage 1B: commercial customer account (credit terms, classification) and delivery sites.
-- The customer itself stays in CRM; this is the fuel-specific layer on top of it.

CREATE TABLE IF NOT EXISTS fuel_customer_accounts (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID          NOT NULL,
    crm_customer_id     UUID          NOT NULL,
    customer_type       VARCHAR(30)   NOT NULL DEFAULT 'OTHER',
    credit_limit        NUMERIC(14,2) NOT NULL DEFAULT 0,
    payment_terms_days  INTEGER       NOT NULL DEFAULT 30,
    credit_hold         BOOLEAN       NOT NULL DEFAULT false,
    credit_hold_reason  VARCHAR(300),
    licence_number      VARCHAR(60),
    notes               VARCHAR(500),
    active              BOOLEAN       NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_fuel_customer_accounts PRIMARY KEY (id),
    CONSTRAINT chk_fuel_cust_type CHECK (customer_type IN ('MINING_COMPANY','MINE_CONTRACTOR','AGRICULTURAL_BUSINESS',
        'COMMERCIAL_FARM','CONSTRUCTION_COMPANY','CIVIL_CONTRACTOR','TRANSPORT_LOGISTICS','FLEET_OPERATOR',
        'FUEL_RETAILER','INDUSTRIAL','GOVERNMENT','RESELLER','OTHER')),
    CONSTRAINT chk_fuel_cust_credit CHECK (credit_limit >= 0),
    CONSTRAINT chk_fuel_cust_terms CHECK (payment_terms_days BETWEEN 0 AND 365)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_fuel_cust_account_crm
    ON fuel_customer_accounts (tenant_id, crm_customer_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS fuel_customer_sites (
    id                      UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID         NOT NULL,
    account_id              UUID         NOT NULL REFERENCES fuel_customer_accounts(id),
    name                    VARCHAR(120) NOT NULL,
    address                 VARCHAR(300),
    contact_name            VARCHAR(120),
    contact_phone           VARCHAR(30),
    delivery_instructions   VARCHAR(500),
    operating_hours         VARCHAR(100),
    active                  BOOLEAN      NOT NULL DEFAULT true,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_fuel_customer_sites PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_fuel_cust_sites_account ON fuel_customer_sites (tenant_id, account_id) WHERE deleted_at IS NULL;

ALTER TABLE fuel_deliveries ADD COLUMN IF NOT EXISTS site_id UUID REFERENCES fuel_customer_sites(id);
