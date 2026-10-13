-- V383 (finance patch F1, 2026-10-13): module permissions for reading the ledger, the accountant practice and expense claims.
--
-- Before this, every Accounting read (journals, trial balance, P&L, balance sheet, bank transactions, VAT 201) needed only
-- USER_READ, so any signed-in user could read the whole ledger; Accountant endpoints accepted USER_READ / USER_CREATE /
-- USER_UPDATE / USER_DELETE as alternatives to ACCOUNTANT_READ / ACCOUNTANT_WRITE; and Expenses used USER_READ to see every
-- claim and USER_UPDATE to approve or pay any claim, including your own.
--
-- Grants are chosen so the people who use these screens today keep working, and everyone else loses access:
--   ACCOUNTING_READ      roles that already hold ACCOUNTING_MANAGE or ACCOUNTING_ADMIN, and every ADMIN and OWNER role.
--   ACCOUNTANT_READ      roles that held USER_READ in a tenant using the accountant module, and every ADMIN and OWNER role.
--   ACCOUNTANT_WRITE     roles that held USER_CREATE, USER_UPDATE or USER_DELETE in such a tenant, and every ADMIN and OWNER role.
--   EXPENSES_READ/MANAGE roles that held USER_UPDATE (today's approvers) in a tenant using the expenses module, and ADMIN and OWNER.
--   EXPENSES_ADMIN       (mark a claim reimbursed, i.e. paying out) ADMIN and OWNER only, so approving and paying are separate.
-- New tenants are unaffected: their ADMIN role receives every permission when the tenant is created (RoleService).
-- Re-runnable.

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'ACCOUNTING_READ',  'View the general ledger, bank transactions, VAT and financial reports'),
    (gen_random_uuid(), 'ACCOUNTANT_READ',  'View accountant practice clients and their records'),
    (gen_random_uuid(), 'ACCOUNTANT_WRITE', 'Change accountant practice clients and their records'),
    (gen_random_uuid(), 'EXPENSES_READ',    'See every employee''s expense claims and the monthly total'),
    (gen_random_uuid(), 'EXPENSES_MANAGE',  'Approve or reject expense claims (never your own)'),
    (gen_random_uuid(), 'EXPENSES_ADMIN',   'Mark approved expense claims as reimbursed')
ON CONFLICT (name) DO NOTHING;

-- Tenants that use a module (trial, active or suspended, so a suspended tenant keeps its access when it comes back).
CREATE TEMP TABLE IF NOT EXISTS v383_module_tenants AS
SELECT tenant_id, module_key FROM tenant_modules WHERE status IN ('TRIAL', 'ACTIVE', 'SUSPENDED');

-- ACCOUNTING_READ
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT r.id, np.id
FROM roles r
JOIN permissions np ON np.name = 'ACCOUNTING_READ'
WHERE r.name IN ('ADMIN', 'OWNER')
   OR EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
              WHERE rp.role_id = r.id AND p.name IN ('ACCOUNTING_MANAGE', 'ACCOUNTING_ADMIN'))
ON CONFLICT DO NOTHING;

-- ACCOUNTANT_READ
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT r.id, np.id
FROM roles r
JOIN permissions np ON np.name = 'ACCOUNTANT_READ'
WHERE r.name IN ('ADMIN', 'OWNER')
   OR (EXISTS (SELECT 1 FROM v383_module_tenants m WHERE m.tenant_id = r.tenant_id AND m.module_key = 'accountant')
       AND EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                   WHERE rp.role_id = r.id AND p.name = 'USER_READ'))
ON CONFLICT DO NOTHING;

-- ACCOUNTANT_WRITE
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT r.id, np.id
FROM roles r
JOIN permissions np ON np.name = 'ACCOUNTANT_WRITE'
WHERE r.name IN ('ADMIN', 'OWNER')
   OR (EXISTS (SELECT 1 FROM v383_module_tenants m WHERE m.tenant_id = r.tenant_id AND m.module_key = 'accountant')
       AND EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                   WHERE rp.role_id = r.id AND p.name IN ('USER_CREATE', 'USER_UPDATE', 'USER_DELETE')))
ON CONFLICT DO NOTHING;

-- EXPENSES_READ and EXPENSES_MANAGE
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT r.id, np.id
FROM roles r
JOIN permissions np ON np.name IN ('EXPENSES_READ', 'EXPENSES_MANAGE')
WHERE r.name IN ('ADMIN', 'OWNER')
   OR (EXISTS (SELECT 1 FROM v383_module_tenants m WHERE m.tenant_id = r.tenant_id AND m.module_key = 'expenses')
       AND EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                   WHERE rp.role_id = r.id AND p.name = 'USER_UPDATE'))
ON CONFLICT DO NOTHING;

-- EXPENSES_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT r.id, np.id
FROM roles r
JOIN permissions np ON np.name = 'EXPENSES_ADMIN'
WHERE r.name IN ('ADMIN', 'OWNER')
ON CONFLICT DO NOTHING;

DROP TABLE IF EXISTS v383_module_tenants;
