-- V853: who gets Security alerts (no-shows, gate overstays, duress and critical alarms, compliance expiries,
-- phone problems). Until now they went to the tenant's first five active users, whatever their job.
--
-- Nothing changes on the day this runs: every role that holds SECURITY_MANAGE gets the new permission, so the same
-- kind of people hear about things. Owners can then take it away from roles that should not get alerts (or give it to a
-- read-only role). A tenant where nobody holds it still falls back to the tenant admins.

INSERT INTO permissions (id, name, description, is_read_only, module_key, resource, verb, sensitivity, requires_second_person, grantable_by)
VALUES
    (gen_random_uuid(), 'SECURITY_ALERTS_RECEIVE', 'Receive Security alerts by e-mail and in the app (no-shows, gate overstays, duress, critical alarms, compliance expiries, phone problems)',
     false, 'security', 'alerts', 'RECEIVE', 'NORMAL', false, 'ADMIN')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, np.id
FROM role_permissions rp
JOIN permissions old ON old.id = rp.permission_id AND old.name = 'SECURITY_MANAGE'
CROSS JOIN permissions np
WHERE np.name = 'SECURITY_ALERTS_RECEIVE'
  AND NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id);
