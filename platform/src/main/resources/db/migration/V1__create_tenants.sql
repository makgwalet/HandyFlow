-- WHY a separate tenants table?
-- Every piece of data in the system belongs to a tenant.
-- This table is the ROOT of our multi-tenancy tree.
-- If a tenant is deleted, cascade deletes everything they own.

CREATE TABLE tenants (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    slug        VARCHAR(100) NOT NULL,  -- URL-friendly identifier e.g. "acme-corp"
    email       VARCHAR(255) NOT NULL,  -- Primary contact email
    status      VARCHAR(50)  NOT NULL DEFAULT 'TRIAL',
    tenant_id   UUID        NOT NULL,   -- Self-referencing for AggregateRoot base class
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP   NOT NULL DEFAULT now(),
    version     BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_tenants PRIMARY KEY (id),
    CONSTRAINT uq_tenants_slug UNIQUE (slug),
    CONSTRAINT uq_tenants_email UNIQUE (email),
    CONSTRAINT chk_tenants_status CHECK (status IN ('TRIAL', 'ACTIVE', 'SUSPENDED', 'CANCELLED'))
);

CREATE INDEX idx_tenants_slug ON tenants(slug);
CREATE INDEX idx_tenants_status ON tenants(status);