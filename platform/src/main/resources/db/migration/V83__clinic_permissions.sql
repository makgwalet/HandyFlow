-- V83 — Clinic module permissions
-- Updated: added CLINIC_LAB_WRITE (missed in original pass)
--
-- RUN (idempotent — ON CONFLICT DO NOTHING throughout):
--   docker cp V83__clinic_permissions.sql handyflow-db:/tmp/V83.sql
--   docker exec -i handyflow-db psql -U handyflow -d handyflow -f /tmp/V83.sql

-- ── 1. Permission definitions ──────────────────────────────────────────────────

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'CLINIC_READ',            'View patients, appointments, consultations, lab results'),
    (gen_random_uuid(), 'CLINIC_WRITE',           'Register patients, book appointments, record consultations'),
    (gen_random_uuid(), 'CLINIC_PRESCRIPTION_WRITE', 'Issue and manage prescriptions'),
    (gen_random_uuid(), 'CLINIC_LAB_WRITE',       'Upload, interpret, review and file lab results (clinician-only)'),
    (gen_random_uuid(), 'CLINIC_BILLING_READ',    'View medical aid claims and billing reports'),
    (gen_random_uuid(), 'CLINIC_BILLING_WRITE',   'Create and submit medical aid claims'),
    (gen_random_uuid(), 'CLINIC_ADMIN',           'Manage practitioners and clinic settings')
ON CONFLICT (name) DO NOTHING;

-- ── 2. OWNER: all clinic permissions ──────────────────────────────────────────

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'OWNER'
  AND p.name IN ('CLINIC_READ','CLINIC_WRITE','CLINIC_PRESCRIPTION_WRITE',
                 'CLINIC_LAB_WRITE','CLINIC_BILLING_READ','CLINIC_BILLING_WRITE','CLINIC_ADMIN')
ON CONFLICT DO NOTHING;

-- ── 3. ADMIN: all except CLINIC_ADMIN ─────────────────────────────────────────

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('CLINIC_READ','CLINIC_WRITE','CLINIC_PRESCRIPTION_WRITE',
                 'CLINIC_LAB_WRITE','CLINIC_BILLING_READ','CLINIC_BILLING_WRITE')
ON CONFLICT DO NOTHING;

-- ── 4. EMPLOYEE (receptionist): read + basic write only ───────────────────────
-- WHY not LAB_WRITE? A receptionist can upload a PDF from an email attachment,
-- but should not interpret results or mark them reviewed (clinician function).

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'EMPLOYEE'
  AND p.name IN ('CLINIC_READ','CLINIC_WRITE')
ON CONFLICT DO NOTHING;

-- ── 5. Verify ─────────────────────────────────────────────────────────────────
-- SELECT u.email, r.name AS role, p.name AS permission
-- FROM users u
-- JOIN user_roles ur ON ur.user_id = u.id
-- JOIN roles r ON r.id = ur.role_id
-- JOIN role_permissions rp ON rp.role_id = r.id
-- JOIN permissions p ON p.id = rp.permission_id
-- WHERE p.name LIKE 'CLINIC_%'
-- ORDER BY u.email, p.name;
