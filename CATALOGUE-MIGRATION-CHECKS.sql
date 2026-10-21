-- CATALOGUE-MIGRATION-CHECKS.sql
-- Read-only checks to run in psql AFTER Flyway has applied V476, V477 and V478.
-- Every query is written so that a healthy database returns ZERO rows (except the two summary queries marked INFO).
-- Nothing here changes data.

-- 1. [must be 0 rows] A role holding SETTINGS_MANAGE is missing one of the baseline Catalogue permissions.
SELECT r.tenant_id, r.name AS role, p.name AS missing
FROM roles r
JOIN role_permissions rp ON rp.role_id = r.id
JOIN permissions sm ON sm.id = rp.permission_id AND sm.name = 'SETTINGS_MANAGE'
CROSS JOIN permissions p
WHERE p.name IN ('CATALOGUE_READ','CATALOGUE_WRITE','CATALOGUE_ARCHIVE','CATALOGUE_IMPORT','CATALOGUE_EXPORT','CATALOGUE_PUBLISH')
  AND NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = r.id AND x.permission_id = p.id);

-- 2. [must be 0 rows] A role holds approval, price or admin authority only because of SETTINGS_MANAGE.
--    (Price read/write/approve and approve are never implied by SETTINGS_MANAGE. A role listed here was granted by hand.
--     Review each row: it is fine if somebody chose it, wrong if it came from the migration.)
SELECT r.tenant_id, r.name AS role, p.name AS permission
FROM roles r
JOIN role_permissions rp ON rp.role_id = r.id
JOIN permissions p ON p.id = rp.permission_id
WHERE p.name IN ('CATALOGUE_APPROVE','CATALOGUE_PRICE_APPROVE','CATALOGUE_PRICE_READ','CATALOGUE_PRICE_WRITE')
ORDER BY r.tenant_id, r.name, p.name;

-- 3. [must be 0 rows] CATALOGUE_ADMIN is held by a role that is not named ADMIN.
SELECT r.tenant_id, r.name AS role
FROM roles r
JOIN role_permissions rp ON rp.role_id = r.id
JOIN permissions p ON p.id = rp.permission_id AND p.name = 'CATALOGUE_ADMIN'
WHERE r.name <> 'ADMIN';

-- 4. [must be 0 rows] A tenant's ADMIN role lacks CATALOGUE_ADMIN.
SELECT r.tenant_id
FROM roles r
WHERE r.name = 'ADMIN'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                  WHERE rp.role_id = r.id AND p.name = 'CATALOGUE_ADMIN');

-- 5. [must be 0 rows] Tenant boundary: a catalogue unit, attribute or value points at another tenant's category or item.
SELECT 'attribute->category' AS problem, d.id FROM catalogue_attribute_definitions d
  JOIN catalogue_categories c ON c.id = d.category_id WHERE c.tenant_id <> d.tenant_id
UNION ALL
SELECT 'value->item', v.id FROM catalogue_item_attribute_values v
  JOIN catalogue_items i ON i.id = v.item_id WHERE i.tenant_id <> v.tenant_id
UNION ALL
SELECT 'value->definition', v.id FROM catalogue_item_attribute_values v
  JOIN catalogue_attribute_definitions d ON d.id = v.definition_id WHERE d.tenant_id <> v.tenant_id
UNION ALL
SELECT 'category->parent', c.id FROM catalogue_categories c
  JOIN catalogue_categories p ON p.id = c.parent_id WHERE p.tenant_id <> c.tenant_id;

-- 6. [must be 0 rows] An item's unit is not one of its tenant's units (the backfill should have made every unit exist).
SELECT i.tenant_id, i.id, i.unit
FROM catalogue_items i
WHERE i.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM catalogue_units u WHERE u.tenant_id = i.tenant_id AND u.deleted_at IS NULL AND lower(u.code) = lower(i.unit));

-- 7. [must be 0 rows] Two spellings of one unit survived in one tenant.
SELECT tenant_id, lower(code), count(*) FROM catalogue_units WHERE deleted_at IS NULL GROUP BY 1, 2 HAVING count(*) > 1;

-- 8. INFO: how many roles hold each Catalogue permission, per tenant. Compare with what you expect.
SELECT r.tenant_id, p.name AS permission, count(*) AS roles
FROM role_permissions rp
JOIN roles r ON r.id = rp.role_id
JOIN permissions p ON p.id = rp.permission_id
WHERE p.name LIKE 'CATALOGUE\_%'
GROUP BY r.tenant_id, p.name
ORDER BY r.tenant_id, p.name;

-- 9. INFO: units created for each tenant by the backfill.
SELECT tenant_id, count(*) AS units FROM catalogue_units WHERE deleted_at IS NULL GROUP BY tenant_id ORDER BY tenant_id;

-- ── Slice 3 (V479): pricing ───────────────────────────────────────────────────

-- 10. [must be 0 rows] A tenant that has catalogue items but no default price list.
SELECT DISTINCT i.tenant_id FROM catalogue_items i
WHERE NOT EXISTS (SELECT 1 FROM catalogue_price_lists l WHERE l.tenant_id = i.tenant_id AND l.is_default AND l.deleted_at IS NULL);

-- 11. [must be 0 rows] A tenant without a catalogue_settings row that existed before V479 (they must keep "no approval").
--     Tenants created after V479 legitimately have no row and are treated as "approval required".
SELECT t.id FROM tenants t
WHERE NOT EXISTS (SELECT 1 FROM catalogue_settings s WHERE s.tenant_id = t.id) AND t.created_at < (SELECT installed_on FROM flyway_schema_history WHERE version = '479');

-- 12. [must be 0 rows] A price change that points at another tenant's item or list.
SELECT c.id FROM catalogue_price_changes c JOIN catalogue_items i ON i.id = c.item_id WHERE i.tenant_id <> c.tenant_id
UNION ALL
SELECT c.id FROM catalogue_price_changes c JOIN catalogue_price_lists l ON l.id = c.price_list_id WHERE l.tenant_id <> c.tenant_id;

-- 13. [must be 0 rows] A scheduled change whose date has passed more than a day ago (the nightly job should have applied it).
SELECT id, item_id, effective_from FROM catalogue_price_changes WHERE status = 'SCHEDULED' AND effective_from < current_date - 1;

-- 14. INFO: price approval setting per tenant (existing tenants should show false).
SELECT tenant_id, price_approval_required, updated_at FROM catalogue_settings ORDER BY tenant_id;

-- 15. INFO: who holds the pricing permissions. None are granted automatically (owner decision 2): grant them on purpose.
SELECT r.tenant_id, r.name AS role, p.name AS permission
FROM role_permissions rp
JOIN roles r ON r.id = rp.role_id
JOIN permissions p ON p.id = rp.permission_id
WHERE p.name IN ('CATALOGUE_PRICE_READ','CATALOGUE_PRICE_WRITE','CATALOGUE_PRICE_APPROVE')
ORDER BY r.tenant_id, r.name, p.name;
