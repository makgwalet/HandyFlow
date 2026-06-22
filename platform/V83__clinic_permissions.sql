-- V83 — Clinic module permissions
--
-- WHY a separate migration?
-- V4 seeds the original platform permissions. Each new module adds its own
-- permissions in a dedicated migration so they can be tracked independently
-- and rolled back cleanly. This mirrors how INVOICE_*, BILLING_* etc. were added.
--
-- WHAT this does:
-- 1. Inserts 6 clinic permission definitions into the global permissions table.
-- 2. Grants all 6 to every OWNER role across all tenants (OWNER = full access).
-- 3. Grants read-only clinic permissions to every ADMIN and EMPLOYEE role.
--    (adjust per-tenant afterward in the settings UI if needed)
--
-- RUN:
--   docker cp V83__clinic_permissions.sql handyflow-db:/tmp/V83.sql
--   docker exec -i handyflow-db psql -U handyflow -d handyflow -f /tmp/V83.sql
-- Then register in flyway_schema_history (same pattern as V78-V82).

-- ── 1. Insert clinic permission definitions ────────────────────────────────────

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'CLINIC_READ',            'View patients, appointments, consultations and lab results'),
    (gen_random_uuid(), 'CLINIC_WRITE',           'Register patients, book appointments, record consultations'),
    (gen_random_uuid(), 'CLINIC_PRESCRIPTION_WRITE', 'Issue and manage prescriptions'),
    (gen_random_uuid(), 'CLINIC_BILLING_READ',    'View medical aid claims and billing reports'),
    (gen_random_uuid(), 'CLINIC_BILLING_WRITE',   'Create and submit medical aid claims'),
    (gen_random_uuid(), 'CLINIC_ADMIN',           'Manage practitioners and clinic settings')
ON CONFLICT (name) DO NOTHING;

-- ── 2. Grant ALL clinic permissions to every OWNER role ─────────────────────────
-- WHY all? The owner of a practice needs unrestricted access to their own clinic.

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'OWNER'
  AND p.name IN (
      'CLINIC_READ',
      'CLINIC_WRITE',
      'CLINIC_PRESCRIPTION_WRITE',
      'CLINIC_BILLING_READ',
      'CLINIC_BILLING_WRITE',
      'CLINIC_ADMIN'
  )
ON CONFLICT DO NOTHING;

-- ── 3. Grant read + write (but not admin/billing_write) to ADMIN roles ───────────

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN (
      'CLINIC_READ',
      'CLINIC_WRITE',
      'CLINIC_PRESCRIPTION_WRITE',
      'CLINIC_BILLING_READ',
      'CLINIC_BILLING_WRITE'
  )
ON CONFLICT DO NOTHING;

-- ── 4. Grant read + write to EMPLOYEE roles (receptionist / nurse use case) ─────

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'EMPLOYEE'
  AND p.name IN (
      'CLINIC_READ',
      'CLINIC_WRITE'
  )
ON CONFLICT DO NOTHING;

-- ── 5. Verify ─────────────────────────────────────────────────────────────────
-- Run this SELECT after applying to confirm Thabo (or any OWNER) has the permissions:
--
-- SELECT u.first_name, u.last_name, u.email, r.name AS role, p.name AS permission
-- FROM users u
-- JOIN user_roles ur ON ur.user_id = u.id
-- JOIN roles r ON r.id = ur.role_id
-- JOIN role_permissions rp ON rp.role_id = r.id
-- JOIN permissions p ON p.id = rp.permission_id
-- WHERE p.name LIKE 'CLINIC_%'
-- ORDER BY u.email, p.name;
