-- V<NEXT>__seed_missing_payrollbureau_and_vip_permissions.sql
-- Confirmed via PermissionConsistencyTest: both permissions are checked
-- in real @PreAuthorize annotations but were never seeded anywhere —
-- PAYROLLBUREAU_* produces the 403-despite-correct-JWT bug reported
-- tonight; VIP_DETAIL_ACCESS makes the entire Close Protection module
-- (CloseProtectionController) inaccessible to everyone, including
-- tenant ADMINs, since createDefaultAdminRole() can only grant
-- permissions that actually exist.

-- ── Payroll Bureau — standard 3-tier set, matches AdminLookupService.
-- createModule()'s naming convention exactly (confirmed against a real
-- JWT tonight: PAYROLLBUREAU_READ/MANAGE/ADMIN). Ordinary business
-- module — auto-granting to every tenant's ADMIN is the established,
-- correct default here.
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'PAYROLLBUREAU_READ',   'View payroll bureau data'),
    (gen_random_uuid(), 'PAYROLLBUREAU_MANAGE', 'Create and manage payroll bureau records'),
    (gen_random_uuid(), 'PAYROLLBUREAU_ADMIN',  'Full administrative access to payroll bureau')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('PAYROLLBUREAU_READ','PAYROLLBUREAU_MANAGE','PAYROLLBUREAU_ADMIN')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- ── VIP_DETAIL_ACCESS — single bespoke gate (Close Protection sub-feature
-- of the Security module), deliberately restricted per Part 9.3
-- compliance intent already documented in the code (real names, medical
-- notes, threat intel). Seeded for every tenant regardless of module
-- status (permissions themselves are global, per Permission.java's own
-- design), but only auto-granted to ADMIN roles of tenants that
-- currently have the Security module active — a tenant without Security
-- has no Close Protection feature to grant access to in the first place.
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'VIP_DETAIL_ACCESS',
     'Access to Close Protection / VIP principal records — real names, medical notes, threat intelligence (Security module)')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
JOIN tenant_modules tm
     ON tm.tenant_id = r.tenant_id
    AND UPPER(tm.module_key) = 'security'
    AND tm.status IN ('ACTIVE', 'TRIAL')
WHERE r.name = 'ADMIN'
  AND p.name = 'VIP_DETAIL_ACCESS'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );