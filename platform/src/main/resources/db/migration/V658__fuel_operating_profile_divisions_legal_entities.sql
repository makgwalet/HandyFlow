-- Fuel Phase 1: operating profile, division activation, legal entities, configuration permissions.
-- Additive only. Existing tenants that already use Fuel get a LEGACY profile so nothing they use is switched off.

CREATE TABLE IF NOT EXISTS fuel_operating_profiles (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    status            VARCHAR(16) NOT NULL DEFAULT 'NOT_STARTED',
    legacy_mode       BOOLEAN NOT NULL DEFAULT FALSE,
    business_size     VARCHAR(16),
    org_structure     VARCHAR(24),
    wizard_step       INT NOT NULL DEFAULT 0,
    wizard_answers    TEXT,
    activated_at      TIMESTAMPTZ,
    activated_by      UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_fuel_profile_status CHECK (status IN ('NOT_STARTED','IN_PROGRESS','ACTIVE')),
    CONSTRAINT chk_fuel_profile_size CHECK (business_size IS NULL OR business_size IN ('SMALL','MEDIUM','LARGE','ENTERPRISE')),
    CONSTRAINT chk_fuel_profile_org CHECK (org_structure IS NULL OR org_structure IN ('ONE_ENTITY','MULTIPLE_ENTITIES','NOT_CONFIGURED'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_fuel_profile_tenant ON fuel_operating_profiles (tenant_id);

CREATE TABLE IF NOT EXISTS fuel_division_activations (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    division              VARCHAR(24) NOT NULL,
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    activated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    activated_by          UUID,
    deactivated_at        TIMESTAMPTZ,
    deactivated_by        UUID,
    deactivation_reason   VARCHAR(300),
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_fuel_division CHECK (division IN ('WHOLESALE_SUPPLY','DISTRIBUTION','RETAIL_FORECOURT','TRUCK_STOP'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_fuel_division_tenant ON fuel_division_activations (tenant_id, division);

CREATE TABLE IF NOT EXISTS fuel_legal_entities (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    name              VARCHAR(160) NOT NULL,
    registration_no   VARCHAR(40),
    tax_no            VARCHAR(40),
    vat_no            VARCHAR(40),
    address           VARCHAR(300),
    status            VARCHAR(12) NOT NULL DEFAULT 'ACTIVE',
    created_by        UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ,
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_fuel_legal_entity_status CHECK (status IN ('ACTIVE','INACTIVE'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_fuel_legal_entity_name ON fuel_legal_entities (tenant_id, lower(name)) WHERE deleted_at IS NULL;

-- Backfill: a tenant that already has Fuel data keeps everything it can use today (legacy mode) until an
-- authorised user completes setup. No division or licence is inferred from the data; legacy mode simply
-- means "do not switch anything off". Tenants with no Fuel data get no row and start at NOT_STARTED.
INSERT INTO fuel_operating_profiles (id, tenant_id, status, legacy_mode)
SELECT gen_random_uuid(), t.tenant_id, 'ACTIVE', TRUE
FROM (
    SELECT tenant_id FROM fuel_tanks
    UNION SELECT tenant_id FROM fuel_depots
    UNION SELECT tenant_id FROM fuel_suppliers
    UNION SELECT tenant_id FROM fuel_customer_accounts
    UNION SELECT tenant_id FROM fuel_sales_orders
    UNION SELECT tenant_id FROM fuel_deliveries
    UNION SELECT tenant_id FROM fuel_pumps
) t
WHERE t.tenant_id IS NOT NULL
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'FUEL_CONFIGURATION_READ', 'View Fuel legal entities and operating configuration details'),
    (gen_random_uuid(), 'FUEL_CONFIGURATION_MANAGE', 'Set up Fuel: activate or deactivate operating divisions and manage legal entities')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions
   SET module_key = 'fuel', resource = 'configuration', verb = 'READ', sensitivity = 'NORMAL',
       requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_CONFIGURATION_READ';
UPDATE permissions
   SET module_key = 'fuel', resource = 'configuration', verb = 'MANAGE', sensitivity = 'SENSITIVE',
       requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'FUEL_CONFIGURATION_MANAGE';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN' AND p.name IN ('FUEL_CONFIGURATION_READ', 'FUEL_CONFIGURATION_MANAGE')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
