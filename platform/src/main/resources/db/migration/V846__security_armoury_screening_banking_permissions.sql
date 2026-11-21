-- V846: split firearm issue/return, screening decisions and guard bank details out of SECURITY_MANAGE
-- (roles audit, chunk 20 finding 2; continues V845).
--
-- Nothing changes on the day this runs: every role that held SECURITY_MANAGE gets the three new permissions.
-- Owners can then take them away from, say, a control-room operator.

INSERT INTO permissions (id, name, description, is_read_only, module_key, resource, verb, sensitivity, requires_second_person, grantable_by)
VALUES
    (gen_random_uuid(), 'SECURITY_ARMOURY_ISSUE',       'Issue firearms and ammunition to guards and take them back',   false, 'security', 'armoury',   'ISSUE',  'SENSITIVE', false, 'ADMIN'),
    (gen_random_uuid(), 'SECURITY_SCREENING_DECIDE',    'Make the clear/reject decision on a guard screening',          false, 'security', 'screening', 'DECIDE', 'SENSITIVE', false, 'ADMIN'),
    (gen_random_uuid(), 'SECURITY_GUARD_BANKING_MANAGE','Set or change a guard''s bank details (used for payroll)',     false, 'security', 'banking',   'MANAGE', 'FINANCIAL', false, 'ADMIN')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, np.id
FROM role_permissions rp
JOIN permissions old ON old.id = rp.permission_id AND old.name = 'SECURITY_MANAGE'
CROSS JOIN permissions np
WHERE np.name IN ('SECURITY_ARMOURY_ISSUE', 'SECURITY_SCREENING_DECIDE', 'SECURITY_GUARD_BANKING_MANAGE')
  AND NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id);
