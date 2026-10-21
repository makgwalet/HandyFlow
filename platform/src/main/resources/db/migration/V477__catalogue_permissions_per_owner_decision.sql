-- V477__catalogue_permissions_per_owner_decision.sql
-- Aligns the Catalogue permissions with the owner's decision on role migration:
--   SETTINGS_MANAGE  -> CATALOGUE_READ, WRITE, IMPORT, EXPORT, PUBLISH, ARCHIVE   (baseline, add-only)
--   ADMIN role       -> CATALOGUE_ADMIN   (configure catalogue rules; same model as POS_ADMIN, APPROVALS_MANAGE)
--   price visibility / price changes / approvals are NOT implied by SETTINGS_MANAGE: assign them explicitly.
-- Idempotent: safe to run on a database that already has some of these rows.

-- 1. The remaining permission definitions (used by later slices).
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'CATALOGUE_IMPORT',         'Import and bulk-update catalogue records'),
    (gen_random_uuid(), 'CATALOGUE_EXPORT',         'Export permitted catalogue data'),
    (gen_random_uuid(), 'CATALOGUE_PUBLISH',        'Publish and unpublish catalogue items'),
    (gen_random_uuid(), 'CATALOGUE_APPROVE',        'Approve or reject catalogue changes'),
    (gen_random_uuid(), 'CATALOGUE_PRICE_APPROVE',  'Approve price changes'),
    (gen_random_uuid(), 'CATALOGUE_ADMIN',          'Configure catalogue rules: units, attributes, approval settings')
ON CONFLICT (name) DO NOTHING;

-- 2. Baseline for roles that already manage settings (adds only; never replaces a role's permissions).
INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, p.id
FROM role_permissions rp
JOIN permissions sm ON sm.id = rp.permission_id AND sm.name = 'SETTINGS_MANAGE'
CROSS JOIN permissions p
WHERE p.name IN ('CATALOGUE_READ','CATALOGUE_WRITE','CATALOGUE_ARCHIVE','CATALOGUE_IMPORT','CATALOGUE_EXPORT','CATALOGUE_PUBLISH')
ON CONFLICT DO NOTHING;

-- 3. The tenant administration role may configure catalogue rules.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN' AND p.name = 'CATALOGUE_ADMIN'
ON CONFLICT DO NOTHING;

-- 4. V476 granted the price permissions to every SETTINGS_MANAGE role. That went beyond the decision:
--    no existing authority covers pricing administration, so nobody gets price access automatically.
--    Withdraw only those grants (roles that hold SETTINGS_MANAGE); grant them again by hand where wanted.
DELETE FROM role_permissions rp
USING permissions pp, role_permissions sm_rp, permissions sm
WHERE rp.permission_id = pp.id AND pp.name IN ('CATALOGUE_PRICE_READ','CATALOGUE_PRICE_WRITE')
  AND sm_rp.role_id = rp.role_id AND sm_rp.permission_id = sm.id AND sm.name = 'SETTINGS_MANAGE';

-- CATALOGUE_APPROVE and CATALOGUE_PRICE_APPROVE are deliberately granted to no role here.
-- The only generic approval permission, APPROVALS_MANAGE, configures approval rules; it does not mean "may approve a
-- price change", and the owner asked that managing settings stay separate from approving commercial changes.
-- To give a role approval authority on purpose:
--   INSERT INTO role_permissions (role_id, permission_id)
--   SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN ('CATALOGUE_APPROVE','CATALOGUE_PRICE_APPROVE')
--   WHERE r.tenant_id = '<tenant>' AND r.name = '<role>' ON CONFLICT DO NOTHING;
