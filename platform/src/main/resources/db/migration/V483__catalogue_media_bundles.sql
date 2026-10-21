-- V483__catalogue_media_bundles.sql
-- Catalogue slice 8a: pictures and documents on an item, and the components of a package (bundle).
-- Files are kept by the shared FileStorageService; this table holds only the opaque storage key and the file's details.

CREATE TABLE IF NOT EXISTS catalogue_item_media (
    id            UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     UUID          NOT NULL,
    item_id       UUID          NOT NULL REFERENCES catalogue_items(id) ON DELETE CASCADE,
    kind          VARCHAR(10)   NOT NULL,
    title         VARCHAR(120),
    alt_text      VARCHAR(200),
    file_name     VARCHAR(200)  NOT NULL,
    content_type  VARCHAR(100)  NOT NULL,
    size_bytes    BIGINT        NOT NULL,
    storage_key   VARCHAR(500)  NOT NULL,
    is_primary    BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order    INT           NOT NULL DEFAULT 0,
    uploaded_by   UUID,
    uploaded_at   TIMESTAMP     NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMP,
    CONSTRAINT pk_catalogue_item_media PRIMARY KEY (id),
    CONSTRAINT ck_catalogue_item_media_kind CHECK (kind IN ('IMAGE','DOCUMENT')),
    CONSTRAINT ck_catalogue_item_media_size CHECK (size_bytes > 0),
    -- only a picture can be the main picture
    CONSTRAINT ck_catalogue_item_media_primary CHECK (NOT is_primary OR kind = 'IMAGE')
);
-- at most one main picture per item
CREATE UNIQUE INDEX IF NOT EXISTS uq_catalogue_item_media_primary ON catalogue_item_media (item_id) WHERE is_primary AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_catalogue_item_media_item ON catalogue_item_media (tenant_id, item_id, sort_order) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS catalogue_bundle_components (
    id                 UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id          UUID           NOT NULL,
    bundle_item_id     UUID           NOT NULL REFERENCES catalogue_items(id) ON DELETE CASCADE,
    component_item_id  UUID           NOT NULL REFERENCES catalogue_items(id),
    quantity           NUMERIC(12,3)  NOT NULL,
    sort_order         INT            NOT NULL DEFAULT 0,
    CONSTRAINT pk_catalogue_bundle_components PRIMARY KEY (id),
    CONSTRAINT uq_catalogue_bundle_component UNIQUE (bundle_item_id, component_item_id),
    CONSTRAINT ck_catalogue_bundle_qty CHECK (quantity > 0),
    CONSTRAINT ck_catalogue_bundle_not_self CHECK (bundle_item_id <> component_item_id)
);
CREATE INDEX IF NOT EXISTS ix_catalogue_bundle_components_component ON catalogue_bundle_components (tenant_id, component_item_id);
