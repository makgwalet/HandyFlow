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

-- ── Slice 4 (V480): variants ──────────────────────────────────────────────────

-- 16. [must be 0 rows] A variant whose parent is in another tenant, is itself a variant, or has been deleted while the variant is live.
SELECT v.id FROM catalogue_items v JOIN catalogue_items p ON p.id = v.parent_id
WHERE v.deleted_at IS NULL AND (p.tenant_id <> v.tenant_id OR p.parent_id IS NOT NULL OR p.deleted_at IS NOT NULL);

-- 17. [must be 0 rows] Two live variants of one parent with the same combination (the unique index should make this impossible).
SELECT parent_id, variant_key, count(*) FROM catalogue_items
WHERE parent_id IS NOT NULL AND deleted_at IS NULL GROUP BY 1, 2 HAVING count(*) > 1;

-- 18. [must be 0 rows] A variant option that points at another tenant's item.
SELECT o.id FROM catalogue_variant_options o JOIN catalogue_items i ON i.id = o.parent_item_id WHERE i.tenant_id <> o.tenant_id;

-- 19. INFO: parents and how many live variants each has.
SELECT p.tenant_id, p.name, count(*) AS variants
FROM catalogue_items p JOIN catalogue_items v ON v.parent_id = p.id AND v.deleted_at IS NULL
WHERE p.deleted_at IS NULL GROUP BY p.tenant_id, p.id, p.name ORDER BY variants DESC LIMIT 50;

-- 20. [must be 0 rows] An import record whose counts do not add up (created + updated + unchanged + skipped + failed should equal total_rows).
SELECT id, total_rows, created, updated, unchanged, skipped, failed FROM catalogue_import_jobs
WHERE created + updated + unchanged + skipped + failed <> total_rows;

-- 21. INFO: the last imports per tenant.
SELECT tenant_id, file_name, mode, total_rows, created, updated, skipped, failed, created_at
FROM catalogue_import_jobs ORDER BY created_at DESC LIMIT 20;

-- 22. [must be 0 rows] A decided approval where the person who asked also decided (the constraint should make this impossible).
SELECT id, item_id FROM catalogue_item_approvals WHERE status IN ('APPROVED','REJECTED') AND decided_by = requested_by;

-- 23. [must be 0 rows] More than one open approval request for the same item.
SELECT item_id, count(*) FROM catalogue_item_approvals WHERE status = 'PENDING' GROUP BY 1 HAVING count(*) > 1;

-- 24. [must be 0 rows] An approved request whose item is still a draft (approval activates the item in the same transaction).
SELECT a.id, i.name FROM catalogue_item_approvals a JOIN catalogue_items i ON i.id = a.item_id
WHERE a.status = 'APPROVED' AND i.status = 'DRAFT' AND i.deleted_at IS NULL AND i.status_changed_at < a.decided_at;

-- 25. INFO: tenants that switched item approval on (should be only those who asked; the default is off).
SELECT tenant_id, price_approval_required, item_approval_required FROM catalogue_settings WHERE item_approval_required;

-- 26. [must be 0 rows] An item with more than one main picture (a unique index should make this impossible).
SELECT item_id, count(*) FROM catalogue_item_media WHERE is_primary AND deleted_at IS NULL GROUP BY 1 HAVING count(*) > 1;

-- 27. [must be 0 rows] Package parts on an item that is not of type PACKAGE.
SELECT c.bundle_item_id, i.name, i.item_type FROM catalogue_bundle_components c JOIN catalogue_items i ON i.id = c.bundle_item_id WHERE i.item_type <> 'PACKAGE';

-- 28. [must be 0 rows] A package that contains another package, or an item that has variants.
SELECT c.bundle_item_id, c.component_item_id FROM catalogue_bundle_components c JOIN catalogue_items p ON p.id = c.component_item_id
WHERE p.item_type = 'PACKAGE' OR EXISTS (SELECT 1 FROM catalogue_items v WHERE v.parent_id = p.id AND v.deleted_at IS NULL);

-- 29. [must be 0 rows] A package part that belongs to a different tenant than the package.
SELECT c.id FROM catalogue_bundle_components c JOIN catalogue_items a ON a.id = c.bundle_item_id JOIN catalogue_items b ON b.id = c.component_item_id
WHERE a.tenant_id <> b.tenant_id OR c.tenant_id <> a.tenant_id;

-- 30. INFO: packages whose parts have been deleted or archived since (they need attention).
SELECT p.tenant_id, p.name AS package, x.name AS part, x.status, x.deleted_at
FROM catalogue_bundle_components c JOIN catalogue_items p ON p.id = c.bundle_item_id AND p.deleted_at IS NULL
JOIN catalogue_items x ON x.id = c.component_item_id WHERE x.deleted_at IS NOT NULL OR x.status = 'ARCHIVED' LIMIT 50;

-- 31. [must be 0 rows] Every history line has a change number (V484 numbers the old lines and the database numbers new ones).
SELECT id FROM catalogue_audit_events WHERE seq IS NULL;

-- 32. [must be 0 rows] A publication whose item, channel and tenant do not agree.
SELECT x.id FROM catalogue_item_channels x JOIN catalogue_items i ON i.id = x.item_id JOIN catalogue_channels c ON c.id = x.channel_id
WHERE i.tenant_id <> x.tenant_id OR c.tenant_id <> x.tenant_id;

-- 33. [must be 0 rows] A channel that sells from a price list that has been deleted.
SELECT c.id, c.code FROM catalogue_channels c JOIN catalogue_price_lists l ON l.id = c.price_list_id WHERE l.deleted_at IS NOT NULL;

-- 34. [must be 0 rows] A variant published on its own (variants go with their main item).
SELECT x.id, i.name FROM catalogue_item_channels x JOIN catalogue_items i ON i.id = x.item_id WHERE i.parent_id IS NOT NULL;

-- 35. INFO: published items that are no longer live (archived or deleted); they drop out of the feeds and can be taken off.
SELECT c.tenant_id, c.code, i.name, i.status, i.deleted_at FROM catalogue_item_channels x JOIN catalogue_channels c ON c.id = x.channel_id
JOIN catalogue_items i ON i.id = x.item_id WHERE i.deleted_at IS NOT NULL OR i.status <> 'ACTIVE' LIMIT 50;
