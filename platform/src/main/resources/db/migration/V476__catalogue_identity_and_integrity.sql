-- V476__catalogue_identity_and_integrity.sql
-- Catalogue slice 1: identity (SKU, barcode), item type, lifecycle status, cost price,
-- uniqueness that ignores deleted rows, an audit trail, and catalogue-specific permissions.

-- ── 1. New columns ────────────────────────────────────────────────────────────
-- sku, barcode and vat_exempt already exist (V41, V55) and are now mapped on the entity.
ALTER TABLE catalogue_items
    ADD COLUMN IF NOT EXISTS item_type         VARCHAR(20)   NOT NULL DEFAULT 'PRODUCT',
    ADD COLUMN IF NOT EXISTS status            VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS status_reason     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS status_changed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS brand             VARCHAR(100),
    ADD COLUMN IF NOT EXISTS cost_price        NUMERIC(15,2);

ALTER TABLE catalogue_items
    ADD CONSTRAINT ck_catalogue_items_type
        CHECK (item_type IN ('PRODUCT','SERVICE','LABOUR','SUBSCRIPTION','PACKAGE','RENTAL','CONSUMABLE','OTHER')),
    ADD CONSTRAINT ck_catalogue_items_status
        CHECK (status IN ('DRAFT','ACTIVE','SUSPENDED','DISCONTINUED','ARCHIVED')),
    ADD CONSTRAINT ck_catalogue_items_cost_nonneg
        CHECK (cost_price IS NULL OR cost_price >= 0);

-- Existing soft-deleted rows were already withdrawn from sale.
UPDATE catalogue_items SET status = 'ARCHIVED' WHERE deleted_at IS NOT NULL;

-- ── 2. Uniqueness that ignores deleted rows ───────────────────────────────────
-- The old constraints counted soft-deleted rows, so a deleted name could never be reused
-- even though the service allowed it (the save then failed with a database error).
ALTER TABLE catalogue_items      DROP CONSTRAINT IF EXISTS uq_catalogue_item_tenant_name;
ALTER TABLE catalogue_categories DROP CONSTRAINT IF EXISTS uq_catalogue_category_tenant_name;

CREATE UNIQUE INDEX IF NOT EXISTS uq_catalogue_items_name_live
    ON catalogue_items (tenant_id, lower(name)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_catalogue_items_sku_live
    ON catalogue_items (tenant_id, lower(sku)) WHERE deleted_at IS NULL AND sku IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_catalogue_items_barcode_live
    ON catalogue_items (tenant_id, lower(barcode)) WHERE deleted_at IS NULL AND barcode IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_catalogue_categories_name_live
    ON catalogue_categories (tenant_id, lower(name)) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_catalogue_items_status
    ON catalogue_items (tenant_id, status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_catalogue_items_sku
    ON catalogue_items (tenant_id, lower(sku)) WHERE sku IS NOT NULL;

-- ── 3. Audit trail ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS catalogue_audit_events (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    entity_type VARCHAR(20)  NOT NULL,
    entity_id   UUID         NOT NULL,
    entity_name VARCHAR(255),
    action      VARCHAR(30)  NOT NULL,
    detail      VARCHAR(1000),
    actor_id    UUID,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT pk_catalogue_audit_events PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_catalogue_audit_entity ON catalogue_audit_events (tenant_id, entity_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_catalogue_audit_tenant ON catalogue_audit_events (tenant_id, created_at DESC);

-- ── 4. Permissions ────────────────────────────────────────────────────────────
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'CATALOGUE_READ',        'View catalogue products and services'),
    (gen_random_uuid(), 'CATALOGUE_WRITE',       'Create and edit catalogue items and categories'),
    (gen_random_uuid(), 'CATALOGUE_ARCHIVE',     'Discontinue, archive or delete catalogue items'),
    (gen_random_uuid(), 'CATALOGUE_PRICE_READ',  'View cost price and margin'),
    (gen_random_uuid(), 'CATALOGUE_PRICE_WRITE', 'Change cost price')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions SET is_read_only = true WHERE name = 'CATALOGUE_READ';

-- Roles that could manage catalogue before (SETTINGS_MANAGE) keep full access.
INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, p.id
FROM role_permissions rp
JOIN permissions sm ON sm.id = rp.permission_id AND sm.name = 'SETTINGS_MANAGE'
CROSS JOIN permissions p
WHERE p.name IN ('CATALOGUE_READ','CATALOGUE_WRITE','CATALOGUE_ARCHIVE','CATALOGUE_PRICE_READ','CATALOGUE_PRICE_WRITE')
ON CONFLICT DO NOTHING;

-- Roles that could read items through invoicing or POS keep read access.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, p.id
FROM role_permissions rp
JOIN permissions src ON src.id = rp.permission_id AND src.name IN ('INVOICE_CREATE','POS_READ','POS_MANAGE','POS_SELL')
CROSS JOIN permissions p
WHERE p.name = 'CATALOGUE_READ'
ON CONFLICT DO NOTHING;
