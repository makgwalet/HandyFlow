-- backlog 4.4 — dedicated POPIA_EXPORT permission
--
-- NUMBERING NOTE: this assumes V244 (backlog 3.4's employee portal
-- migration, delivered earlier this session) has already been applied.
-- If anything else has landed in between on your end, renumber to the
-- real next Vnn before running.
--
-- Mirrors AdminLookupService.createModule()'s own permission-seeding
-- pattern exactly (verified directly against its real SQL): INSERT ...
-- ON CONFLICT (name) DO NOTHING for the permission row, then grant via
-- role_permissions.
INSERT INTO permissions (id, name, description)
VALUES (gen_random_uuid(), 'POPIA_EXPORT',
        'Export full POPIA data subject history, including internal user IDs of who accessed/modified the record')
ON CONFLICT (name) DO NOTHING;

-- Grant to every role that CURRENTLY holds CUSTOMER_DELETE — preserves
-- exactly who can already do this today. Deliberately not just "grant to
-- ADMIN" (createModule()'s own default target): some tenant may have a
-- custom role, not literally named 'ADMIN', that was granted
-- CUSTOMER_DELETE specifically to allow POPIA exports under the old
-- over-broad check — narrowing to ADMIN-only here would silently revoke
-- their access, which is not what this fix is for. This is a permission
-- SPLIT (same access, better-scoped authority), not a permission CUT.
INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, p.id
FROM role_permissions rp
JOIN permissions old_p ON old_p.id = rp.permission_id AND old_p.name = 'CUSTOMER_DELETE'
JOIN permissions p ON p.name = 'POPIA_EXPORT'
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions rp2
    WHERE rp2.role_id = rp.role_id AND rp2.permission_id = p.id
);

-- Also grant to every tenant's ADMIN role directly — same defensive
-- always-grant-ADMIN behavior AdminLookupService.createModule() applies
-- to every newly created permission, as a backstop in case any tenant's
-- ADMIN role doesn't already hold CUSTOMER_DELETE for some reason.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name = 'POPIA_EXPORT'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );