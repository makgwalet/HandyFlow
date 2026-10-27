-- Inspection templates, inspection runs and corrective actions for the internal facilities module.

CREATE TABLE IF NOT EXISTS facility_inspection_templates (
    id             UUID PRIMARY KEY,
    tenant_id      UUID         NOT NULL,
    name           VARCHAR(255) NOT NULL,
    description    VARCHAR(1000),
    asset_category VARCHAR(60),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    revision       INT          NOT NULL DEFAULT 1,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_facility_insp_template_name ON facility_inspection_templates (tenant_id, LOWER(name));

CREATE TABLE IF NOT EXISTS facility_inspection_template_items (
    id          UUID PRIMARY KEY,
    template_id UUID         NOT NULL REFERENCES facility_inspection_templates (id) ON DELETE CASCADE,
    position    INT          NOT NULL,
    prompt      VARCHAR(300) NOT NULL,
    critical    BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_facility_insp_template_items ON facility_inspection_template_items (template_id, position);

CREATE TABLE IF NOT EXISTS facility_inspections (
    id                UUID PRIMARY KEY,
    tenant_id         UUID         NOT NULL,
    template_id       UUID         NOT NULL,
    template_name     VARCHAR(255) NOT NULL,
    template_revision INT          NOT NULL,
    site_id           UUID         NOT NULL,
    asset_id          UUID,
    inspector_user_id UUID,
    inspector_name    VARCHAR(255),
    status            VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS',
    result            VARCHAR(10),
    notes             VARCHAR(2000),
    cancel_reason     VARCHAR(500),
    started_at        TIMESTAMPTZ  NOT NULL,
    completed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    version           BIGINT
);
CREATE INDEX IF NOT EXISTS idx_facility_inspections_site ON facility_inspections (tenant_id, site_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_facility_inspections_failed ON facility_inspections (tenant_id, completed_at DESC) WHERE result = 'FAIL';

CREATE TABLE IF NOT EXISTS facility_inspection_results (
    id            UUID PRIMARY KEY,
    inspection_id UUID         NOT NULL REFERENCES facility_inspections (id) ON DELETE CASCADE,
    item_id       UUID         NOT NULL,
    position      INT          NOT NULL,
    prompt        VARCHAR(300) NOT NULL,
    critical      BOOLEAN      NOT NULL DEFAULT FALSE,
    outcome       VARCHAR(10),
    comment       VARCHAR(1000),
    recorded_at   TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_facility_insp_results ON facility_inspection_results (inspection_id, position);

CREATE TABLE IF NOT EXISTS facility_corrective_actions (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID          NOT NULL,
    source_type        VARCHAR(20)   NOT NULL,
    source_id          UUID,
    site_id            UUID          NOT NULL,
    asset_id           UUID,
    description        VARCHAR(1000) NOT NULL,
    priority           VARCHAR(20)   NOT NULL,
    critical           BOOLEAN       NOT NULL DEFAULT FALSE,
    status             VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    due_date           DATE          NOT NULL,
    work_order_id      UUID,
    closure_note       VARCHAR(1000),
    closed_at          TIMESTAMPTZ,
    created_by_user_id UUID,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    version            BIGINT
);
CREATE INDEX IF NOT EXISTS idx_facility_actions_open ON facility_corrective_actions (tenant_id, due_date) WHERE status IN ('OPEN','IN_PROGRESS');
CREATE INDEX IF NOT EXISTS idx_facility_actions_source ON facility_corrective_actions (tenant_id, source_type, source_id);
CREATE INDEX IF NOT EXISTS idx_facility_actions_site ON facility_corrective_actions (tenant_id, site_id);
