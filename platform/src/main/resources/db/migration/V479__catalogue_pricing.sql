-- V479__catalogue_pricing.sql
-- Catalogue slice 3: price lists, dated price changes with an approval step, minimum price, and the per-tenant
-- "price changes need approval" setting.
-- catalogue_items.default_price stays the Retail price that Invoicing, POS and Supply Chain read; a change to the
-- Retail list updates it when it takes effect. Other lists get their price from the latest applied change.

ALTER TABLE catalogue_items ADD COLUMN IF NOT EXISTS min_price NUMERIC(15,2);
ALTER TABLE catalogue_items
    ADD CONSTRAINT ck_catalogue_items_min_nonneg CHECK (min_price IS NULL OR min_price >= 0);

-- ── Price lists ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS catalogue_price_lists (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    code        VARCHAR(30)  NOT NULL,
    name        VARCHAR(60)  NOT NULL,
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMP,
    deleted_by  UUID,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_catalogue_price_lists PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_catalogue_price_lists_code_live
    ON catalogue_price_lists (tenant_id, lower(code)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_catalogue_price_lists_one_default
    ON catalogue_price_lists (tenant_id) WHERE is_default AND deleted_at IS NULL;

-- Every tenant that already has catalogue items gets its Retail list now; others get it the first time the list is opened.
INSERT INTO catalogue_price_lists (tenant_id, code, name, is_default, sort_order)
SELECT DISTINCT tenant_id, 'RETAIL', 'Retail', TRUE, 0 FROM catalogue_items
ON CONFLICT DO NOTHING;

-- ── Price changes (history and workflow in one table) ─────────────────────────
CREATE TABLE IF NOT EXISTS catalogue_price_changes (
    id             UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id      UUID          NOT NULL,
    item_id        UUID          NOT NULL REFERENCES catalogue_items(id) ON DELETE CASCADE,
    price_list_id  UUID          NOT NULL REFERENCES catalogue_price_lists(id),
    old_price      NUMERIC(15,2),
    new_price      NUMERIC(15,2) NOT NULL,
    effective_from DATE          NOT NULL,
    reason         VARCHAR(255),
    status         VARCHAR(12)   NOT NULL,
    proposed_by    UUID,
    proposed_at    TIMESTAMP     NOT NULL DEFAULT now(),
    decided_by     UUID,
    decided_at     TIMESTAMP,
    decision_note  VARCHAR(255),
    applied_at     TIMESTAMP,
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_catalogue_price_changes PRIMARY KEY (id),
    CONSTRAINT ck_catalogue_price_changes_status CHECK (status IN ('PENDING','SCHEDULED','APPLIED','REJECTED','CANCELLED')),
    CONSTRAINT ck_catalogue_price_changes_price CHECK (new_price >= 0 AND (old_price IS NULL OR old_price >= 0))
);
CREATE INDEX IF NOT EXISTS idx_catalogue_price_changes_item ON catalogue_price_changes (tenant_id, item_id, proposed_at DESC);
CREATE INDEX IF NOT EXISTS idx_catalogue_price_changes_status ON catalogue_price_changes (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_catalogue_price_changes_due ON catalogue_price_changes (effective_from) WHERE status = 'SCHEDULED';
-- At most one change waiting for a decision per item and list.
CREATE UNIQUE INDEX IF NOT EXISTS uq_catalogue_price_changes_one_pending
    ON catalogue_price_changes (item_id, price_list_id) WHERE status = 'PENDING';

-- ── Tenant settings ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS catalogue_settings (
    tenant_id                UUID      NOT NULL,
    price_approval_required  BOOLEAN   NOT NULL,
    updated_at               TIMESTAMP NOT NULL DEFAULT now(),
    updated_by               UUID,
    CONSTRAINT pk_catalogue_settings PRIMARY KEY (tenant_id),
    CONSTRAINT fk_catalogue_settings_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);
-- Owner decision: existing tenants keep today's behaviour (no approval step). A tenant with no row, that is every
-- tenant created from now on, is treated as "approval required" by the application.
INSERT INTO catalogue_settings (tenant_id, price_approval_required)
SELECT id, FALSE FROM tenants
ON CONFLICT DO NOTHING;
