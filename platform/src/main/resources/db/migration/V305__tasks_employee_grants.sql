-- V305__tasks_employee_grants.sql
--
-- V37 granted TASKS_READ / TASKS_MANAGE / TASKS_ADMIN to ADMIN roles only, so in a tenant that
-- subscribes to Tasks nobody but an admin could even see the module. This gives EMPLOYEE roles
-- read and manage access, following V83 (Clinic) and V88 (Projects, the nearest sibling).
-- TASKS_ADMIN (create boards, edit columns, archive) stays with ADMIN.
--
-- OPTIONAL: if Tasks should stay admin-only, do not apply this migration. Roles created for
-- tenants after this runs are a matter for tenant provisioning, not for a migration.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'EMPLOYEE'
  AND p.name IN ('TASKS_READ', 'TASKS_MANAGE')
ON CONFLICT DO NOTHING;
