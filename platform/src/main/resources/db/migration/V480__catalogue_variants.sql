-- V480__catalogue_variants.sql
-- Catalogue slice 4: variants. A variant is itself a catalogue item (own id, SKU, barcode, price) that points at a parent
-- item, so quotes, invoices, POS and Supply Chain keep selling it unchanged. A parent that has live variants is not
-- offered for sale itself; that is worked out from its variants, not stored.

ALTER TABLE catalogue_items ADD COLUMN IF NOT EXISTS parent_id      UUID REFERENCES catalogue_items(id);
ALTER TABLE catalogue_items ADD COLUMN IF NOT EXISTS variant_key    VARCHAR(400);   -- size=large|colour=red (lower case, unique per parent)
ALTER TABLE catalogue_items ADD COLUMN IF NOT EXISTS variant_label  VARCHAR(300);   -- Large / Red
ALTER TABLE catalogue_items ADD COLUMN IF NOT EXISTS variant_values VARCHAR(1100);  -- Large|Red, in option order

ALTER TABLE catalogue_items ADD CONSTRAINT ck_catalogue_items_variant_shape
    CHECK ((parent_id IS NULL AND variant_key IS NULL AND variant_label IS NULL AND variant_values IS NULL)
        OR (parent_id IS NOT NULL AND parent_id <> id AND variant_key IS NOT NULL AND variant_label IS NOT NULL AND variant_values IS NOT NULL));

CREATE INDEX IF NOT EXISTS idx_catalogue_items_parent ON catalogue_items (tenant_id, parent_id) WHERE parent_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_catalogue_items_variant_key
    ON catalogue_items (parent_id, variant_key) WHERE parent_id IS NOT NULL AND deleted_at IS NULL;

-- The options of a parent (Size, Colour) with every value offered, so unused values are remembered.
CREATE TABLE IF NOT EXISTS catalogue_variant_options (
    id             UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id      UUID          NOT NULL,
    parent_item_id UUID          NOT NULL REFERENCES catalogue_items(id) ON DELETE CASCADE,
    name           VARCHAR(30)   NOT NULL,
    sort_order     INT           NOT NULL DEFAULT 0,
    option_values  VARCHAR(1100) NOT NULL,   -- Small|Medium|Large
    CONSTRAINT pk_catalogue_variant_options PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_catalogue_variant_options_name ON catalogue_variant_options (parent_item_id, lower(name));
