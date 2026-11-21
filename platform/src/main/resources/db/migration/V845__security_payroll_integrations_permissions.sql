-- V845: split payroll and integrations out of SECURITY_MANAGE (roles audit, chunk 20 findings 2 and 3).
--
-- Before: anyone with SECURITY_MANAGE could create, approve and pay payroll and create API keys/webhooks, and anyone with
-- SECURITY_READ could read and export payroll. Now each has its own permission, so a tenant can give a control-room
-- operator MANAGE without handing them payroll.
--
-- Nothing changes for existing roles on the day this runs: every role that held SECURITY_MANAGE gets all new permissions,
-- and every role that held SECURITY_READ gets SECURITY_PAYROLL_READ. Owners/admins can now remove them where wanted.

INSERT INTO permissions (id, name, description, is_read_only, module_key, resource, verb, sensitivity, requires_second_person, grantable_by)
VALUES
    (gen_random_uuid(), 'SECURITY_PAYROLL_READ',      'View guard payroll periods, lines, exports and payslips',        true,  'security', 'payroll',      'READ',    'FINANCIAL', false, 'ADMIN'),
    (gen_random_uuid(), 'SECURITY_PAYROLL_MANAGE',    'Create payroll periods and set grade rates',                     false, 'security', 'payroll',      'MANAGE',  'FINANCIAL', false, 'ADMIN'),
    (gen_random_uuid(), 'SECURITY_PAYROLL_APPROVE',   'Approve a payroll period (not the person who created it)',      false, 'security', 'payroll',      'APPROVE', 'FINANCIAL', true,  'ADMIN'),
    (gen_random_uuid(), 'SECURITY_PAYROLL_PAY',       'Mark a payroll period paid (not the person who approved it)',   false, 'security', 'payroll',      'PAY',     'FINANCIAL', true,  'ADMIN'),
    (gen_random_uuid(), 'SECURITY_INTEGRATIONS_MANAGE','Create and revoke public API keys and webhooks',               false, 'security', 'integrations', 'MANAGE',  'SENSITIVE', false, 'ADMIN')
ON CONFLICT (name) DO NOTHING;

-- Keep current behaviour: holders of SECURITY_MANAGE get all five.
INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, np.id
FROM role_permissions rp
JOIN permissions old ON old.id = rp.permission_id AND old.name = 'SECURITY_MANAGE'
CROSS JOIN permissions np
WHERE np.name IN ('SECURITY_PAYROLL_READ', 'SECURITY_PAYROLL_MANAGE', 'SECURITY_PAYROLL_APPROVE',
                  'SECURITY_PAYROLL_PAY', 'SECURITY_INTEGRATIONS_MANAGE')
  AND NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id);

-- Keep current behaviour: holders of SECURITY_READ keep reading payroll.
INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, np.id
FROM role_permissions rp
JOIN permissions old ON old.id = rp.permission_id AND old.name = 'SECURITY_READ'
CROSS JOIN permissions np
WHERE np.name = 'SECURITY_PAYROLL_READ'
  AND NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id);
