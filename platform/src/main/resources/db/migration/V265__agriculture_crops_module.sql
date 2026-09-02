-- ============================================================================
-- V265__agriculture_crops_module.sql
--
-- Agriculture module — Build Increment 2: Crops.
--
-- *** VERSION NUMBER NOT CONFIRMED *** — assumes it follows this module's
-- own V264 (Increment 1: Farm Foundation + Livestock) sequentially. Read
-- the real Flyway migration history (flyway_schema_history / the actual
-- db/migration directory) before applying — same standing caveat V264
-- itself carried and every prior module this engagement has carried.
--
-- FK CONSTRAINT CONVENTION — carried forward unchanged from V264: every
-- cross-entity reference below (farm_id, production_area_id, season_id,
-- crop_type_id, crop_cycle_id, inventory_item_id, etc.) is a plain UUID
-- column with NO database-level FK constraint, matching this module's own
-- V264 convention and the platform's documented "no hard FK constraints
-- across modules" multi-tenant convention, applied here consistently even
-- for references within this module. Flagged explicitly in the final
-- report; please confirm before merging.
--
-- NO module_catalogue / permissions / role_permissions changes here —
-- Crops reuses Increment 1's 'agriculture' module key and its three
-- AGRICULTURE_READ / AGRICULTURE_MANAGE / AGRICULTURE_ADMIN permissions
-- verbatim (see this migration's own task brief). Nothing in this file
-- touches those tables.
-- ============================================================================

-- ── ag_crop_types ────────────────────────────────────────────────────────
CREATE TABLE ag_crop_types (
    id                       UUID PRIMARY KEY,
    tenant_id                UUID NOT NULL,
    name                     VARCHAR(255) NOT NULL,
    category                 VARCHAR(20) NOT NULL,
    typical_growing_days     INTEGER,
    default_unit_of_measure  VARCHAR(30) NOT NULL DEFAULT 'kg',
    status                   VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    deleted_at               TIMESTAMPTZ,
    version                  BIGINT
);
CREATE INDEX idx_ag_crop_types_tenant ON ag_crop_types (tenant_id);

-- ── ag_seasons ───────────────────────────────────────────────────────────
CREATE TABLE ag_seasons (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    farm_id     UUID NOT NULL,
    name        VARCHAR(255) NOT NULL,
    start_date  DATE NOT NULL,
    end_date    DATE,
    status      VARCHAR(20) NOT NULL DEFAULT 'PLANNING',
    notes       TEXT,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    deleted_at  TIMESTAMPTZ,
    version     BIGINT
);
CREATE INDEX idx_ag_seasons_tenant ON ag_seasons (tenant_id);
CREATE INDEX idx_ag_seasons_farm ON ag_seasons (farm_id);

-- ── ag_crop_cycles ───────────────────────────────────────────────────────
-- The Crops sub-domain's central tracking unit — one planting instance —
-- playing the same structural role ag_groups plays for Livestock. See
-- AgCropCycle's own Javadoc for why there is no separate individual/group
-- table pair here.
CREATE TABLE ag_crop_cycles (
    id                       UUID PRIMARY KEY,
    tenant_id                UUID NOT NULL,
    farm_id                  UUID NOT NULL,
    production_area_id       UUID NOT NULL,
    enterprise_id            UUID,
    season_id                UUID,
    crop_type_id             UUID NOT NULL,
    variety                  VARCHAR(255),
    cycle_name               VARCHAR(255),
    area_planted_hectares    NUMERIC(10,2) NOT NULL,
    planting_date            DATE,
    expected_harvest_date    DATE,
    seed_inventory_item_id   UUID,
    seed_quantity            NUMERIC(12,3),
    seed_source               VARCHAR(255),
    status                   VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    notes                    TEXT,
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL,
    deleted_at                TIMESTAMPTZ,
    version                   BIGINT
);
CREATE INDEX idx_ag_crop_cycles_tenant ON ag_crop_cycles (tenant_id);
CREATE INDEX idx_ag_crop_cycles_farm ON ag_crop_cycles (farm_id);
CREATE INDEX idx_ag_crop_cycles_production_area ON ag_crop_cycles (production_area_id);
CREATE INDEX idx_ag_crop_cycles_enterprise ON ag_crop_cycles (enterprise_id);
CREATE INDEX idx_ag_crop_cycles_season ON ag_crop_cycles (season_id);
CREATE INDEX idx_ag_crop_cycles_crop_type ON ag_crop_cycles (crop_type_id);
CREATE INDEX idx_ag_crop_cycles_tenant_status ON ag_crop_cycles (tenant_id, status);

-- ── ag_input_applications (append-only) ─────────────────────────────────
CREATE TABLE ag_input_applications (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    crop_cycle_id         UUID NOT NULL,
    application_date      DATE NOT NULL,
    input_type            VARCHAR(20) NOT NULL,
    inventory_item_id     UUID,
    product_used          VARCHAR(255),
    quantity_applied      NUMERIC(12,3) NOT NULL,
    unit_of_measure       VARCHAR(30) NOT NULL,
    application_method    VARCHAR(255),
    applied_by            UUID,
    applied_by_name       VARCHAR(255),
    labor_hours           NUMERIC(8,2),
    cost                  NUMERIC(12,2),
    weather_conditions    VARCHAR(255),
    notes                 TEXT,
    created_at            TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_ag_input_applications_tenant ON ag_input_applications (tenant_id);
CREATE INDEX idx_ag_input_applications_crop_cycle ON ag_input_applications (crop_cycle_id);
CREATE INDEX idx_ag_input_applications_inventory_item ON ag_input_applications (inventory_item_id);

-- ── ag_scouting_records (mutable) ────────────────────────────────────────
CREATE TABLE ag_scouting_records (
    id                       UUID PRIMARY KEY,
    tenant_id                UUID NOT NULL,
    crop_cycle_id            UUID NOT NULL,
    scouting_date            DATE NOT NULL,
    observation_type         VARCHAR(30) NOT NULL,
    severity                 VARCHAR(10) NOT NULL DEFAULT 'LOW',
    description              TEXT NOT NULL,
    recommended_action       TEXT,
    scouted_by               UUID,
    scouted_by_name          VARCHAR(255),
    follow_up_date           DATE,
    follow_up_acknowledged   BOOLEAN NOT NULL DEFAULT FALSE,
    status                   VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    notes                    TEXT,
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    version                  BIGINT
);
CREATE INDEX idx_ag_scouting_records_tenant ON ag_scouting_records (tenant_id);
CREATE INDEX idx_ag_scouting_records_crop_cycle ON ag_scouting_records (crop_cycle_id);
-- Backs AgScoutingRecordRepository.findFollowUpDueAcrossTenants() — the
-- daily sweep scans every tenant's rows, so no leading tenant_id column
-- here. Mirrors idx_ag_health_events_next_due from V264 exactly.
CREATE INDEX idx_ag_scouting_records_follow_up
    ON ag_scouting_records (follow_up_date, follow_up_acknowledged)
    WHERE follow_up_date IS NOT NULL;

-- ── ag_harvest_records (append-only) ─────────────────────────────────────
CREATE TABLE ag_harvest_records (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    crop_cycle_id         UUID NOT NULL,
    harvest_date          DATE NOT NULL,
    quantity_harvested    NUMERIC(14,3) NOT NULL,
    unit_of_measure       VARCHAR(30) NOT NULL,
    quality_grade         VARCHAR(100),
    moisture_content      NUMERIC(5,2),
    storage_location      VARCHAR(255),
    harvested_by          UUID,
    harvested_by_name     VARCHAR(255),
    labor_hours           NUMERIC(8,2),
    notes                 TEXT,
    created_at            TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_ag_harvest_records_tenant ON ag_harvest_records (tenant_id);
CREATE INDEX idx_ag_harvest_records_crop_cycle ON ag_harvest_records (crop_cycle_id);
