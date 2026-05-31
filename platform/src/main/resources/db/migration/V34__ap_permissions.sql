-- V34__ap_permissions.sql
-- Adds Accounts Payable permissions to the system.
-- WHY separate migration? Permissions are system-level and must exist
-- before roles can reference them. Adding post-V33 ensures AP tables exist first.

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'AP_READ',   'View accounts payable bills and batches'),
    (gen_random_uuid(), 'AP_MANAGE', 'Create, approve and pay supplier bills')
ON CONFLICT (name) DO NOTHING;

-- Grant to all existing ADMIN roles across all tenants
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('AP_READ', 'AP_MANAGE')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
