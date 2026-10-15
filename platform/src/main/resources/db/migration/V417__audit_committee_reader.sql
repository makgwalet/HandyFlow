-- A17: a read-only permission for audit committee members. It opens the committee report and nothing else:
-- no universe, workpapers, findings or evidence. Administrators give it to a role of their choosing
-- (for example one called "Audit Committee Reader"), so each company decides who sits on its committee.
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'AUDIT_COMMITTEE_READ', 'View the audit committee report (plan delivery, approved reports, overdue high-risk actions, risk and control position)')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name = 'AUDIT_COMMITTEE_READ'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
