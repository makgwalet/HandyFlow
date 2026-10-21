-- V478__catalogue_units_category_tree_attributes.sql
-- Catalogue slice 2: units of measure, category tree, attribute definitions and values.
-- Additive. catalogue_items.unit stays a text column holding the unit code (other modules read it),
-- but it must now match a unit of the tenant.

-- ── 1. Units of measure ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS catalogue_units (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(60)  NOT NULL,
    kind        VARCHAR(10)  NOT NULL DEFAULT 'OTHER',
    decimals    INT          NOT NULL DEFAULT 2,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMP,
    deleted_by  UUID,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_catalogue_units PRIMARY KEY (id),
    CONSTRAINT ck_catalogue_units_kind CHECK (kind IN ('COUNT','LENGTH','AREA','VOLUME','WEIGHT','TIME','OTHER')),
    CONSTRAINT ck_catalogue_units_decimals CHECK (decimals BETWEEN 0 AND 4)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_catalogue_units_code_live
    ON catalogue_units (tenant_id, lower(code)) WHERE deleted_at IS NULL;

-- Backfill 1: every unit the tenant's items already use, merged ignoring case and spacing.
-- The most common spelling wins, so "Each", "each" and " each" become one unit.
INSERT INTO catalogue_units (tenant_id, code, name, kind, decimals)
SELECT tenant_id, code, code, 'OTHER', 2
FROM (
    SELECT DISTINCT ON (tenant_id, lower(btrim(unit)))
           tenant_id, btrim(unit) AS code
    FROM (
        SELECT tenant_id, unit, count(*) AS n
        FROM catalogue_items
        WHERE unit IS NOT NULL AND btrim(unit) <> ''
        GROUP BY tenant_id, unit
    ) used
    ORDER BY tenant_id, lower(btrim(unit)), n DESC, btrim(unit)
) canonical
ON CONFLICT DO NOTHING;

-- Backfill 2: the standard set for every tenant that already has catalogue items
-- (other tenants get it the first time they open the unit list). Never overrides a unit from backfill 1.
INSERT INTO catalogue_units (tenant_id, code, name, kind, decimals)
SELECT t.tenant_id, s.code, s.name, s.kind, s.decimals
FROM (SELECT DISTINCT tenant_id FROM catalogue_items) t
CROSS JOIN (VALUES
    ('each',  'Each',        'COUNT',  0),
    ('box',   'Box',         'COUNT',  0),
    ('pack',  'Pack',        'COUNT',  0),
    ('set',   'Set',         'COUNT',  0),
    ('hour',  'Hour',        'TIME',   2),
    ('day',   'Day',         'TIME',   2),
    ('m',     'Metre',       'LENGTH', 2),
    ('m²',    'Square metre','AREA',   2),
    ('m³',    'Cubic metre', 'VOLUME', 2),
    ('kg',    'Kilogram',    'WEIGHT', 3),
    ('t',     'Tonne',       'WEIGHT', 3),
    ('l',     'Litre',       'VOLUME', 2)
) AS s(code, name, kind, decimals)
WHERE NOT EXISTS (SELECT 1 FROM catalogue_units u WHERE u.tenant_id = t.tenant_id AND lower(u.code) = lower(s.code) AND u.deleted_at IS NULL);

-- Make item text agree with the canonical spelling so lookups by code match.
UPDATE catalogue_items i
SET unit = u.code
FROM catalogue_units u
WHERE u.tenant_id = i.tenant_id AND lower(u.code) = lower(btrim(i.unit)) AND u.deleted_at IS NULL
  AND i.unit IS DISTINCT FROM u.code;

CREATE INDEX IF NOT EXISTS idx_catalogue_items_unit ON catalogue_items (tenant_id, lower(unit)) WHERE deleted_at IS NULL;

-- ── 2. Category tree ──────────────────────────────────────────────────────────
ALTER TABLE catalogue_categories
    ADD COLUMN IF NOT EXISTS parent_id UUID REFERENCES catalogue_categories(id);
CREATE INDEX IF NOT EXISTS idx_catalogue_categories_parent ON catalogue_categories (tenant_id, parent_id);

-- A name only has to be unique among siblings ("Other" may exist under two parents).
-- Existing categories are all roots with unique names, so the new index cannot fail on existing data.
DROP INDEX IF EXISTS uq_catalogue_categories_name_live;
CREATE UNIQUE INDEX IF NOT EXISTS uq_catalogue_categories_sibling_name_live
    ON catalogue_categories (tenant_id, COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), lower(name))
    WHERE deleted_at IS NULL;

-- ── 3. Attribute definitions and values ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS catalogue_attribute_definitions (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    category_id UUID         REFERENCES catalogue_categories(id),   -- null = applies to every item
    code        VARCHAR(40)  NOT NULL,
    label       VARCHAR(60)  NOT NULL,
    data_type   VARCHAR(10)  NOT NULL,
    required    BOOLEAN      NOT NULL DEFAULT FALSE,
    options     TEXT,                                               -- LIST: one option per line
    min_value   NUMERIC(19,6),
    max_value   NUMERIC(19,6),
    max_length  INT,
    sort_order  INT          NOT NULL DEFAULT 0,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMP,
    deleted_by  UUID,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_catalogue_attribute_definitions PRIMARY KEY (id),
    CONSTRAINT ck_catalogue_attr_type CHECK (data_type IN ('TEXT','NUMBER','BOOLEAN','DATE','LIST'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_catalogue_attr_code_live
    ON catalogue_attribute_definitions (tenant_id, lower(code)) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_catalogue_attr_category
    ON catalogue_attribute_definitions (tenant_id, category_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS catalogue_item_attribute_values (
    id            UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     UUID          NOT NULL,
    item_id       UUID          NOT NULL REFERENCES catalogue_items(id) ON DELETE CASCADE,
    definition_id UUID          NOT NULL REFERENCES catalogue_attribute_definitions(id),
    value_text    VARCHAR(500)  NOT NULL,
    updated_at    TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT pk_catalogue_item_attribute_values PRIMARY KEY (id),
    CONSTRAINT uq_catalogue_item_attribute UNIQUE (item_id, definition_id)
);
CREATE INDEX IF NOT EXISTS idx_catalogue_attr_values_def ON catalogue_item_attribute_values (tenant_id, definition_id);
