-- V230__seed_agency_permissions.sql
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'RECRUITMENTAGENCY_READ',   'View recruitment agency data'),
    (gen_random_uuid(), 'RECRUITMENTAGENCY_MANAGE', 'Create and manage recruitment agency records'),
    (gen_random_uuid(), 'RECRUITMENTAGENCY_ADMIN',  'Full administrative access to recruitment agency'),
    (gen_random_uuid(), 'BOOKINGAGENCY_READ',   'View booking agency data'),
    (gen_random_uuid(), 'BOOKINGAGENCY_MANAGE', 'Create and manage booking agency records'),
    (gen_random_uuid(), 'BOOKINGAGENCY_ADMIN',  'Full administrative access to booking agency')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('RECRUITMENTAGENCY_READ','RECRUITMENTAGENCY_MANAGE','RECRUITMENTAGENCY_ADMIN',
                 'BOOKINGAGENCY_READ','BOOKINGAGENCY_MANAGE','BOOKINGAGENCY_ADMIN')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);