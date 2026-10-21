-- V484__catalogue_channels_sync.sql
-- Catalogue slice 8b: sales channels and what is published to them, and a change number on the history so other systems
-- can ask "what changed since I last looked" (the delta API).

CREATE TABLE IF NOT EXISTS catalogue_channels (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL,
    code           VARCHAR(30)  NOT NULL,
    name           VARCHAR(80)  NOT NULL,
    kind           VARCHAR(12)  NOT NULL,
    price_list_id  UUID         REFERENCES catalogue_price_lists(id),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT pk_catalogue_channels PRIMARY KEY (id),
    CONSTRAINT ck_catalogue_channels_kind CHECK (kind IN ('POS','WEB','MARKETPLACE','PRINT','OTHER')),
    CONSTRAINT ck_catalogue_channels_code CHECK (code ~ '^[a-z0-9][a-z0-9-]{1,29}$')
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_catalogue_channels_code ON catalogue_channels (tenant_id, code);

CREATE TABLE IF NOT EXISTS catalogue_item_channels (
    id            UUID       NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     UUID       NOT NULL,
    item_id       UUID       NOT NULL REFERENCES catalogue_items(id) ON DELETE CASCADE,
    channel_id    UUID       NOT NULL REFERENCES catalogue_channels(id),
    published_at  TIMESTAMP  NOT NULL DEFAULT now(),
    published_by  UUID,
    CONSTRAINT pk_catalogue_item_channels PRIMARY KEY (id),
    CONSTRAINT uq_catalogue_item_channel UNIQUE (item_id, channel_id)
);
CREATE INDEX IF NOT EXISTS ix_catalogue_item_channels_channel ON catalogue_item_channels (tenant_id, channel_id);

-- A running change number on the history. Existing rows are numbered in the order they are found; new rows in the order written.
ALTER TABLE catalogue_audit_events ADD COLUMN IF NOT EXISTS seq BIGINT GENERATED ALWAYS AS IDENTITY;
CREATE INDEX IF NOT EXISTS ix_catalogue_audit_seq ON catalogue_audit_events (tenant_id, seq);
