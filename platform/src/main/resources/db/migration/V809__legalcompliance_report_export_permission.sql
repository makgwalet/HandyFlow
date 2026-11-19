-- ============================================================================
-- Legal / Compliance — permission to export reports. Depends on V292. Confirm the next free Flyway version before applying.
-- Viewing a report needs only LEGALCOMPLIANCE_READ; downloading one (CSV) needs this permission, and every export is written to the regulatory audit trail.
-- ============================================================================

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'LEGALCOMPLIANCE_REPORT_EXPORT', 'Download Legal and Compliance reports as files (each export is audited)')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name = 'LEGALCOMPLIANCE_REPORT_EXPORT'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
