-- Run this BEFORE deploying the BookingsController fix, so you know
-- which roles need BOOKINGS_READ/MANAGE/ADMIN granted to avoid locking
-- anyone out of bookings they currently have access to.
SELECT r.name AS role_name
FROM roles r
JOIN role_permissions rp ON rp.role_id = r.id
JOIN permissions p ON p.id = rp.permission_id
WHERE p.name = 'USER_READ'
ORDER BY r.name;

-- Once you've decided which of those roles should keep bookings access,
-- grant the new permissions the same way this migration grants them to
-- ADMIN, e.g.:
--
-- INSERT INTO role_permissions (role_id, permission_id)
-- SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
-- WHERE r.name = 'YOUR_ROLE_NAME'
-- AND p.name IN ('BOOKINGS_READ', 'BOOKINGS_MANAGE', 'BOOKINGS_ADMIN')
-- AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);