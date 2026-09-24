-- src/main/resources/db/migration/V292__complianceservices_phase1_clients.sql
--
-- Phase 1 of complianceservices: the client company itself. Resolves the
-- open design question left in complianceservices/package-info.java — a
-- dedicated entity that OPTIONALLY references an existing CRM customer,
-- rather than extending crm.Customer directly. Chosen because it doesn't
-- force a schema change onto CRM's own domain model for a still-unproven
-- second module, and a compliance client carries data a sales customer
-- never would (mandate/authority-to-act, compliance-specific contacts).

CREATE TABLE compliance_service_clients (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,              -- the SERVICE PROVIDER's own tenant (the accounting practice, consultancy, etc.)
    name              VARCHAR(255) NOT NULL,       -- the client company's name — works standalone even with no CRM link
    crm_customer_id   UUID,                        -- nullable reference into crm's own customers table — not a DB FK, CRM lives in another module
    contact_email     VARCHAR(255),
    contact_phone     VARCHAR(50),
    mandate_notes     TEXT,                        -- what authority/mandate the service provider has to act for this client
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, INACTIVE
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by        UUID,
    version           BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_compliance_clients_tenant ON compliance_service_clients(tenant_id);
CREATE INDEX idx_compliance_clients_crm_customer ON compliance_service_clients(crm_customer_id);
-- A given CRM customer shouldn't be linked as a compliance client twice for the same tenant
CREATE UNIQUE INDEX uq_compliance_clients_tenant_crm_customer
    ON compliance_service_clients(tenant_id, crm_customer_id) WHERE crm_customer_id IS NOT NULL;

-- Module catalogue + permissions — same pattern as V288's own registration
-- for compliancetender.

INSERT INTO module_catalogue (key, name, description, category, monthly_price, is_active)
VALUES ('complianceservices', 'Compliance Services',
        'Manage compliance and tender work for many client companies — for accounting practices, business consultancies, and compliance service providers.',
        'Compliance', 499.00, TRUE)
ON CONFLICT (key) DO NOTHING;

INSERT INTO permissions (id, name, description, is_read_only)
VALUES
    (gen_random_uuid(), 'COMPLIANCE_SERVICES_READ',   'View compliance service clients', TRUE),
    (gen_random_uuid(), 'COMPLIANCE_SERVICES_MANAGE', 'Create and update compliance service clients', FALSE),
    (gen_random_uuid(), 'COMPLIANCE_SERVICES_ADMIN',  'Delete compliance service clients', FALSE)
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
AND p.name IN ('COMPLIANCE_SERVICES_READ', 'COMPLIANCE_SERVICES_MANAGE', 'COMPLIANCE_SERVICES_ADMIN')
AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);
