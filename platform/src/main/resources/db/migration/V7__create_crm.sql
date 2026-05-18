-- src/main/resources/db/migration/V7__create_crm.sql

CREATE TABLE customers (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255),
    phone           VARCHAR(50),
    -- WHY JSONB for address? Address formats vary by country.
    -- South Africa: street, suburb, city, province, postal_code
    -- JSONB lets us store this flexibly without 6 nullable columns
    address         JSONB,
    tax_number      VARCHAR(50),  -- VAT registration number
    notes           TEXT,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMP,
    deleted_by      UUID,
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_customers PRIMARY KEY (id),
    CONSTRAINT uq_customers_tenant_email
        UNIQUE (tenant_id, email)
        -- WHY? Same email can exist across tenants
        -- but must be unique within a tenant
);

CREATE TABLE contacts (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    customer_id     UUID        NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    email           VARCHAR(255),
    phone           VARCHAR(50),
    job_title       VARCHAR(100),
    is_primary      BOOLEAN     NOT NULL DEFAULT false,
    notes           TEXT,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMP,
    deleted_by      UUID,
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_contacts PRIMARY KEY (id),
    CONSTRAINT fk_contacts_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_customers_tenant ON customers(tenant_id)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_contacts_customer ON contacts(customer_id)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_customers_name ON customers(tenant_id, name)
    WHERE deleted_at IS NULL;