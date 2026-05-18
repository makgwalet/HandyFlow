-- src/main/resources/db/migration/V6__create_product_catalogue.sql

CREATE TABLE catalogue_categories (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    sort_order  INTEGER     NOT NULL DEFAULT 0,
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP   NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMP,
    deleted_by  UUID,
    version     BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_catalogue_categories PRIMARY KEY (id),
    CONSTRAINT uq_catalogue_category_tenant_name
        UNIQUE (tenant_id, name)
);

CREATE TABLE catalogue_items (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    category_id     UUID,
    name            VARCHAR(255)    NOT NULL,
    description     TEXT,
    unit            VARCHAR(50)     NOT NULL DEFAULT 'each',
    default_price   NUMERIC(15,2)   NOT NULL DEFAULT 0,
    vat_rate        NUMERIC(5,2)    NOT NULL DEFAULT 15.00,
    is_active       BOOLEAN         NOT NULL DEFAULT true,
    created_at      TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMP,
    deleted_by      UUID,
    version         BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_catalogue_items PRIMARY KEY (id),
    CONSTRAINT fk_catalogue_items_category
        FOREIGN KEY (category_id)
        REFERENCES catalogue_categories(id)
        ON DELETE SET NULL,
    -- WHY? Item names unique per tenant (not global)
    CONSTRAINT uq_catalogue_item_tenant_name
        UNIQUE (tenant_id, name)
);

CREATE INDEX idx_catalogue_items_tenant ON catalogue_items(tenant_id)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_catalogue_items_category ON catalogue_items(category_id)
    WHERE deleted_at IS NULL;
-- WHY partial index? We only ever query active (non-deleted) items
-- in normal operations. Partial index is smaller and faster.