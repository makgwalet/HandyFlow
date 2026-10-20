-- Rename to the next free Flyway version, e.g. V125__fleet_inspections.sql

CREATE TABLE fleet_inspection_templates (
    id                     UUID PRIMARY KEY,
    tenant_id              UUID         NOT NULL,
    name                   VARCHAR(150) NOT NULL,
    inspection_type        VARCHAR(30)  NOT NULL,
    description            TEXT,
    interval_days          INTEGER,                    -- NULL = ad-hoc, no schedule
    applies_to_vehicle_type VARCHAR(50),               -- NULL = every vehicle type
    signature_required     BOOLEAN      NOT NULL DEFAULT FALSE,
    active                 BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ  NOT NULL,
    updated_at             TIMESTAMPTZ  NOT NULL,
    deleted_at             TIMESTAMPTZ,
    deleted_by             UUID,
    version                BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_fleet_inspection_templates_name
    ON fleet_inspection_templates (tenant_id, LOWER(name)) WHERE deleted_at IS NULL;

CREATE TABLE fleet_inspection_template_items (
    id                     UUID PRIMARY KEY,
    template_id            UUID         NOT NULL REFERENCES fleet_inspection_templates (id) ON DELETE CASCADE,
    section                VARCHAR(100) NOT NULL,
    label                  VARCHAR(255) NOT NULL,
    sort_order             INTEGER      NOT NULL,
    required               BOOLEAN      NOT NULL DEFAULT TRUE,
    photo_required_on_fail BOOLEAN      NOT NULL DEFAULT FALSE,
    severity               VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM'
);
CREATE INDEX idx_fleet_inspection_template_items_template ON fleet_inspection_template_items (template_id);

CREATE TABLE fleet_inspections (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID         NOT NULL,
    vehicle_id              UUID         NOT NULL REFERENCES fleet_vehicles (id),
    template_id             UUID         NOT NULL,   -- deliberately no FK: templates can be edited/deleted, inspections are history
    template_name           VARCHAR(150) NOT NULL,   -- snapshot
    inspection_type         VARCHAR(30)  NOT NULL,   -- snapshot
    signature_required      BOOLEAN      NOT NULL DEFAULT FALSE, -- snapshot
    inspector_name          VARCHAR(255),
    inspector_id            UUID,
    driver_id               UUID,
    odometer                INTEGER,
    status                  VARCHAR(20)  NOT NULL,   -- IN_PROGRESS | COMPLETED | CANCELLED
    verdict                 VARCHAR(10),             -- PASS | FAIL, set on completion
    failed_count            INTEGER      NOT NULL DEFAULT 0,
    started_at              TIMESTAMPTZ  NOT NULL,
    completed_at            TIMESTAMPTZ,
    completed_by            UUID,
    notes                   TEXT,
    signature_key           VARCHAR(500),
    signature_content_type  VARCHAR(100),
    signer_name             VARCHAR(255),
    signed_at               TIMESTAMPTZ,
    version                 BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_fleet_inspections_vehicle ON fleet_inspections (tenant_id, vehicle_id, started_at DESC);
CREATE INDEX idx_fleet_inspections_last_completed
    ON fleet_inspections (tenant_id, vehicle_id, template_id, completed_at) WHERE status = 'COMPLETED';

CREATE TABLE fleet_inspection_results (
    id                     UUID PRIMARY KEY,
    inspection_id          UUID         NOT NULL REFERENCES fleet_inspections (id) ON DELETE CASCADE,
    template_item_id       UUID,                      -- informational only, no FK
    section                VARCHAR(100) NOT NULL,     -- snapshot of the question as asked
    label                  VARCHAR(255) NOT NULL,
    sort_order             INTEGER      NOT NULL,
    required               BOOLEAN      NOT NULL,
    photo_required_on_fail BOOLEAN      NOT NULL,
    severity               VARCHAR(20)  NOT NULL,
    outcome                VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    comment                TEXT
);
CREATE INDEX idx_fleet_inspection_results_inspection ON fleet_inspection_results (inspection_id);

CREATE TABLE fleet_inspection_photos (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    inspection_id UUID         NOT NULL REFERENCES fleet_inspections (id) ON DELETE CASCADE,
    result_id     UUID         REFERENCES fleet_inspection_results (id) ON DELETE CASCADE,
    file_name     VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    storage_key   VARCHAR(500) NOT NULL,
    caption       VARCHAR(500),
    uploaded_by   UUID,
    uploaded_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_fleet_inspection_photos_inspection ON fleet_inspection_photos (inspection_id);

CREATE TABLE fleet_issues (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID         NOT NULL,
    vehicle_id           UUID         NOT NULL REFERENCES fleet_vehicles (id),
    source               VARCHAR(20)  NOT NULL,       -- INSPECTION | MANUAL
    source_inspection_id UUID,
    source_result_id     UUID,
    title                VARCHAR(255) NOT NULL,
    description          TEXT,
    severity             VARCHAR(20)  NOT NULL,
    status               VARCHAR(20)  NOT NULL,       -- OPEN | IN_PROGRESS | RESOLVED | DISMISSED
    reported_by          UUID,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    resolved_at          TIMESTAMPTZ,
    resolved_by          UUID,
    resolution_note      TEXT,
    deleted_at           TIMESTAMPTZ
);
CREATE INDEX idx_fleet_issues_vehicle ON fleet_issues (tenant_id, vehicle_id, status);
CREATE INDEX idx_fleet_issues_tenant_status ON fleet_issues (tenant_id, status, created_at DESC);