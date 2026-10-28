-- Recruiter: finer permissions for the actions that most need a named owner.
--   RECRUITER_AUDIT    audit trail, who-opened-CVs, undelivered candidate emails
--   RECRUITER_CV       open a candidate's CV and download a scorecard
--   RECRUITER_REPORTS  see and export reports
--   RECRUITER_OFFER    send, record the answer to, and revise an offer
--   RECRUITER_HIRE     mark a candidate Hired and convert them to an employee
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'RECRUITER_AUDIT',   'See the Recruiter audit trail, CV access and undelivered candidate emails'),
    (gen_random_uuid(), 'RECRUITER_CV',      'Open candidates'' CVs and download scorecards'),
    (gen_random_uuid(), 'RECRUITER_REPORTS', 'See and export Recruiter reports'),
    (gen_random_uuid(), 'RECRUITER_OFFER',   'Send, record the answer to, and revise job offers'),
    (gen_random_uuid(), 'RECRUITER_HIRE',    'Mark a candidate as hired and convert them to an employee')
ON CONFLICT (name) DO NOTHING;

-- Nothing changes on day one: each new permission goes to every role that already held the permission that used to govern the action.
-- A company can then take one away from a role without taking away the rest of that role.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
FROM role_permissions rp
JOIN permissions src ON src.id = rp.permission_id
JOIN (VALUES
    ('RECRUITER_ADMIN',  'RECRUITER_AUDIT'),
    ('RECRUITER_READ',   'RECRUITER_CV'),
    ('RECRUITER_READ',   'RECRUITER_REPORTS'),
    ('RECRUITER_MANAGE', 'RECRUITER_OFFER'),
    ('RECRUITER_MANAGE', 'RECRUITER_HIRE'),
    ('RECRUITER_ADMIN',  'RECRUITER_HIRE')
) AS m(old_name, new_name) ON m.old_name = src.name
JOIN permissions np ON np.name = m.new_name
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id
);
