-- Recruiter: a named approver permission, and a privacy-notice link shown with (and recorded against) the careers consent.

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'RECRUITER_APPROVE', 'Approve jobs and offers before they go out')
ON CONFLICT (name) DO NOTHING;

-- Nothing changes on day one: every role that could decide an approval (RECRUITER_ADMIN) keeps that ability. A company can then take
-- RECRUITER_APPROVE away from roles that should no longer decide, and reminders follow the permission instead of every administrator.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, ap.id
FROM role_permissions rp
JOIN permissions src ON src.id = rp.permission_id AND src.name = 'RECRUITER_ADMIN'
JOIN permissions ap  ON ap.name = 'RECRUITER_APPROVE'
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = ap.id
);

ALTER TABLE rec_settings     ADD COLUMN IF NOT EXISTS privacy_notice_url VARCHAR(500);
-- The link the applicant was shown when they agreed, kept with the consent so a later change of link does not rewrite what they saw.
ALTER TABLE rec_applications ADD COLUMN IF NOT EXISTS consent_privacy_url VARCHAR(500);
