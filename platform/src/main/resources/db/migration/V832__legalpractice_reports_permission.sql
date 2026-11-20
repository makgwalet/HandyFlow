-- V832: Legal Practice reports. One permission for all firm reports, because they show fees, debtors and trust balances.
-- Confirm the next free Flyway version (V831 is matter tasks).
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'LEGALPRACTICE_REPORT_VIEW', 'View and export firm reports: matters, unbilled work, debtors, billing, trust balances')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions SET module_key = 'legalpractice', resource = 'report', verb = 'VIEW', sensitivity = 'FINANCIAL', requires_second_person = FALSE, grantable_by = 'ADMIN'
 WHERE name = 'LEGALPRACTICE_REPORT_VIEW';

-- Financial: only roles that hold LEGALPRACTICE_ADMIN get it. Give it to others in Roles.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions src ON src.id = rp.permission_id AND src.name = 'LEGALPRACTICE_ADMIN'
  JOIN permissions np ON np.name = 'LEGALPRACTICE_REPORT_VIEW'
 WHERE NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id);
