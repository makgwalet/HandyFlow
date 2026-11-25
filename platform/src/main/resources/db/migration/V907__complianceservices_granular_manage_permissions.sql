-- Compliance Services: narrower write permissions that sit alongside COMPLIANCE_SERVICES_MANAGE (the same approach as Fuel, V666).
-- COMPLIANCE_SERVICES_MANAGE still works everywhere it did, so existing roles keep exactly the access they had.
-- Each new permission opens one area only, so a tenant can give, say, quote writing to someone without the rest. Viewing still needs COMPLIANCE_SERVICES_READ.
-- Only ADMIN is given the new permissions here; other roles keep COMPLIANCE_SERVICES_MANAGE and nothing changes for them.
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'COMPLIANCE_SERVICES_BILLING_MANAGE', 'Compliance Services: manage agreements and billing events, without the rest of Compliance Services management'),
    (gen_random_uuid(), 'COMPLIANCE_SERVICES_QUOTES_MANAGE', 'Compliance Services: write and submit quotes, without the rest of Compliance Services management'),
    (gen_random_uuid(), 'COMPLIANCE_SERVICES_PROVIDERS_MANAGE', 'Compliance Services: manage outside providers, their accreditations and the work given to them'),
    (gen_random_uuid(), 'COMPLIANCE_SERVICES_PORTAL_MANAGE', 'Compliance Services: invite client users to the client portal'),
    (gen_random_uuid(), 'COMPLIANCE_SERVICES_IMPORT_MANAGE', 'Compliance Services: check client and registration files for import (an administrator still imports them)'),
    (gen_random_uuid(), 'COMPLIANCE_SERVICES_QUALITY_MANAGE', 'Compliance Services: carry out quality reviews of work')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions SET module_key = 'complianceservices', resource = 'billing', verb = 'MANAGE', sensitivity = 'FINANCIAL', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'COMPLIANCE_SERVICES_BILLING_MANAGE';
UPDATE permissions SET module_key = 'complianceservices', resource = 'quotes', verb = 'MANAGE', sensitivity = 'FINANCIAL', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'COMPLIANCE_SERVICES_QUOTES_MANAGE';
UPDATE permissions SET module_key = 'complianceservices', resource = 'providers', verb = 'MANAGE', sensitivity = 'SENSITIVE', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'COMPLIANCE_SERVICES_PROVIDERS_MANAGE';
UPDATE permissions SET module_key = 'complianceservices', resource = 'portal', verb = 'MANAGE', sensitivity = 'SENSITIVE', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'COMPLIANCE_SERVICES_PORTAL_MANAGE';
UPDATE permissions SET module_key = 'complianceservices', resource = 'import', verb = 'MANAGE', sensitivity = 'SENSITIVE', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'COMPLIANCE_SERVICES_IMPORT_MANAGE';
UPDATE permissions SET module_key = 'complianceservices', resource = 'quality', verb = 'MANAGE', sensitivity = 'NORMAL', requires_second_person = false, grantable_by = 'ADMIN'
 WHERE name = 'COMPLIANCE_SERVICES_QUALITY_MANAGE';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('COMPLIANCE_SERVICES_BILLING_MANAGE', 'COMPLIANCE_SERVICES_QUOTES_MANAGE', 'COMPLIANCE_SERVICES_PROVIDERS_MANAGE',
                 'COMPLIANCE_SERVICES_PORTAL_MANAGE', 'COMPLIANCE_SERVICES_IMPORT_MANAGE', 'COMPLIANCE_SERVICES_QUALITY_MANAGE')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
