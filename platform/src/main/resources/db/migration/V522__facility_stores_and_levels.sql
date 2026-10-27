-- Multiple stores: stock is held per store. The item keeps its total on-hand and average cost; the per-store levels
-- always add up to the item's total.
CREATE TABLE IF NOT EXISTS facility_stores (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    code        VARCHAR(30)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    site_id     UUID,
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_facility_store_code UNIQUE (tenant_id, code)
);
-- At most one default store per tenant.
CREATE UNIQUE INDEX IF NOT EXISTS uq_facility_store_default ON facility_stores (tenant_id) WHERE is_default;

CREATE TABLE IF NOT EXISTS facility_stock_levels (
    id          UUID PRIMARY KEY,
    tenant_id   UUID           NOT NULL,
    store_id    UUID           NOT NULL REFERENCES facility_stores (id),
    item_id     UUID           NOT NULL REFERENCES facility_stock_items (id),
    on_hand     NUMERIC(14, 3) NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_facility_stock_level UNIQUE (store_id, item_id),
    CONSTRAINT ck_facility_stock_level_on_hand CHECK (on_hand >= 0)
);
CREATE INDEX IF NOT EXISTS idx_facility_stock_levels_item ON facility_stock_levels (tenant_id, item_id);

ALTER TABLE facility_stock_movements ADD COLUMN IF NOT EXISTS store_id UUID;
ALTER TABLE facility_goods_receipts ADD COLUMN IF NOT EXISTS store_id UUID;

-- Transfers between stores are two ledger lines, out and in.
ALTER TABLE facility_stock_movements DROP CONSTRAINT IF EXISTS ck_facility_stock_movement_type;
ALTER TABLE facility_stock_movements ADD CONSTRAINT ck_facility_stock_movement_type
    CHECK (movement_type IN ('RECEIPT', 'ISSUE', 'RETURN', 'ADJUSTMENT', 'TRANSFER_OUT', 'TRANSFER_IN'));

-- Existing stock goes into one default store per tenant that has any, so nothing changes for current users.
INSERT INTO facility_stores (id, tenant_id, code, name, is_default, active, created_at, updated_at)
SELECT gen_random_uuid(), t.tenant_id, 'MAIN', 'Main store', TRUE, TRUE, now(), now()
FROM (SELECT DISTINCT tenant_id FROM facility_stock_items) t
WHERE NOT EXISTS (SELECT 1 FROM facility_stores s WHERE s.tenant_id = t.tenant_id);

INSERT INTO facility_stock_levels (id, tenant_id, store_id, item_id, on_hand, updated_at)
SELECT gen_random_uuid(), i.tenant_id, s.id, i.id, i.on_hand, now()
FROM facility_stock_items i
JOIN facility_stores s ON s.tenant_id = i.tenant_id AND s.is_default
WHERE i.on_hand > 0
  AND NOT EXISTS (SELECT 1 FROM facility_stock_levels l WHERE l.store_id = s.id AND l.item_id = i.id);

UPDATE facility_stock_movements m SET store_id = s.id
FROM facility_stores s
WHERE s.tenant_id = m.tenant_id AND s.is_default AND m.store_id IS NULL;

UPDATE facility_goods_receipts g SET store_id = s.id
FROM facility_stores s
WHERE s.tenant_id = g.tenant_id AND s.is_default AND g.store_id IS NULL;
