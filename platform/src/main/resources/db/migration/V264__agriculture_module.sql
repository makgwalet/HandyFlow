-- ============================================================================
-- V264__agriculture_module.sql
--
-- Agriculture module — Build Increment 1: Farm Foundation + Livestock.
--
-- *** VERSION NUMBER NOT CONFIRMED ***
-- V264 assumes it follows Bookkeeping's own V263 sequentially (per the
-- Bookkeeping status report). Read the real Flyway migration history
-- (flyway_schema_history / the actual db/migration directory) before
-- applying — same standing caveat as every prior module this engagement.
--
-- FK CONSTRAINT CONVENTION — NOT CONFIRMED AGAINST A REAL EARTHMOVING/FLEET
-- MIGRATION (src/main/resources is excluded from this session's synced
-- source). Every cross-entity reference below (farm_id, production_area_id,
-- animal_id, group_id, inventory_item_id, sire_id/dam_id, etc.) is a plain
-- UUID column with NO database-level FK constraint, matching the platform's
-- documented "no hard FK constraints across modules" multi-tenant convention
-- and applied here consistently even for references within this module —
-- the safer, more conservative default given the convention could not be
-- directly re-verified for earthmoving's own migration this session. Flagged
-- explicitly in the final report; please confirm against a real earthmoving/
-- fleet migration before merging and add FKs here if that is in fact the
-- established in-module convention.
-- ============================================================================

-- ── ag_farms ─────────────────────────────────────────────────────────────
CREATE TABLE ag_farms (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    name                 VARCHAR(255) NOT NULL,
    farm_type            VARCHAR(30) NOT NULL,
    registration_number  VARCHAR(100),
    province             VARCHAR(100),
    region               VARCHAR(100),
    gps_latitude         DOUBLE PRECISION,
    gps_longitude        DOUBLE PRECISION,
    total_hectares       NUMERIC(12,2),
    manager_id           UUID,
    manager_name         VARCHAR(255),
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes                TEXT,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    deleted_at           TIMESTAMPTZ,
    version              BIGINT
);
CREATE INDEX idx_ag_farms_tenant ON ag_farms (tenant_id);
CREATE INDEX idx_ag_farms_tenant_status ON ag_farms (tenant_id, status);

-- ── ag_production_areas ─────────────────────────────────────────────────
CREATE TABLE ag_production_areas (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    farm_id        UUID NOT NULL,
    name           VARCHAR(255) NOT NULL,
    area_type      VARCHAR(20) NOT NULL,
    size_hectares  NUMERIC(10,2),
    capacity       INTEGER,
    soil_type      VARCHAR(100),
    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes          TEXT,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    deleted_at     TIMESTAMPTZ,
    version        BIGINT
);
CREATE INDEX idx_ag_production_areas_tenant ON ag_production_areas (tenant_id);
CREATE INDEX idx_ag_production_areas_farm ON ag_production_areas (farm_id);

-- ── ag_enterprises ───────────────────────────────────────────────────────
CREATE TABLE ag_enterprises (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    farm_id          UUID NOT NULL,
    name             VARCHAR(255) NOT NULL,
    enterprise_type  VARCHAR(20) NOT NULL,
    species_focus    VARCHAR(255),
    start_date       DATE,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes            TEXT,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    deleted_at       TIMESTAMPTZ,
    version          BIGINT
);
CREATE INDEX idx_ag_enterprises_tenant ON ag_enterprises (tenant_id);
CREATE INDEX idx_ag_enterprises_farm ON ag_enterprises (farm_id);

-- ── ag_species ───────────────────────────────────────────────────────────
CREATE TABLE ag_species (
    id                       UUID PRIMARY KEY,
    tenant_id                UUID NOT NULL,
    name                     VARCHAR(255) NOT NULL,
    category                 VARCHAR(20) NOT NULL,
    default_unit_of_measure  VARCHAR(30) NOT NULL DEFAULT 'head',
    tracking_mode            VARCHAR(20) NOT NULL DEFAULT 'BOTH',
    gestation_days           INTEGER,
    maturity_weight_kg       NUMERIC(10,2),
    status                   VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    deleted_at               TIMESTAMPTZ,
    version                  BIGINT
);
CREATE INDEX idx_ag_species_tenant ON ag_species (tenant_id);

-- ── ag_animals ───────────────────────────────────────────────────────────
CREATE TABLE ag_animals (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    farm_id             UUID NOT NULL,
    production_area_id  UUID,
    enterprise_id       UUID,
    species_id          UUID NOT NULL,
    tag_number          VARCHAR(100) NOT NULL,
    name                VARCHAR(255),
    breed               VARCHAR(255),
    sex                 VARCHAR(15) NOT NULL,
    date_of_birth       DATE,
    estimated_age       BOOLEAN NOT NULL DEFAULT FALSE,
    sire_id             UUID,
    dam_id              UUID,
    acquisition_type    VARCHAR(20) NOT NULL,
    acquisition_date    DATE NOT NULL,
    acquisition_cost    NUMERIC(12,2),
    current_weight_kg   NUMERIC(10,2),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    deleted_at          TIMESTAMPTZ,
    version             BIGINT
);
CREATE INDEX idx_ag_animals_tenant ON ag_animals (tenant_id);
CREATE INDEX idx_ag_animals_farm ON ag_animals (farm_id);
CREATE INDEX idx_ag_animals_production_area ON ag_animals (production_area_id);
CREATE INDEX idx_ag_animals_enterprise ON ag_animals (enterprise_id);
CREATE INDEX idx_ag_animals_species ON ag_animals (species_id);
CREATE INDEX idx_ag_animals_tenant_status ON ag_animals (tenant_id, status);
-- Partial unique index: uniqueness only enforced across live (non-deleted)
-- rows, matching the "tag number can be re-used once an animal is soft-
-- deleted" expectation and mirroring earthmoving's own partial-uniqueness
-- convention for fleet_number.
CREATE UNIQUE INDEX uq_ag_animals_tenant_farm_tag
    ON ag_animals (tenant_id, farm_id, tag_number)
    WHERE deleted_at IS NULL;

-- ── ag_groups ────────────────────────────────────────────────────────────
CREATE TABLE ag_groups (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    farm_id             UUID NOT NULL,
    production_area_id  UUID,
    enterprise_id       UUID,
    species_id          UUID NOT NULL,
    batch_number        VARCHAR(100) NOT NULL,
    breed               VARCHAR(255),
    initial_count       INTEGER NOT NULL,
    current_count       INTEGER NOT NULL,
    average_weight_kg   NUMERIC(10,2),
    origin_date         DATE NOT NULL,
    acquisition_type    VARCHAR(20) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    deleted_at          TIMESTAMPTZ,
    version             BIGINT
);
CREATE INDEX idx_ag_groups_tenant ON ag_groups (tenant_id);
CREATE INDEX idx_ag_groups_farm ON ag_groups (farm_id);
CREATE INDEX idx_ag_groups_production_area ON ag_groups (production_area_id);
CREATE INDEX idx_ag_groups_enterprise ON ag_groups (enterprise_id);
CREATE INDEX idx_ag_groups_species ON ag_groups (species_id);
CREATE INDEX idx_ag_groups_tenant_status ON ag_groups (tenant_id, status);
CREATE UNIQUE INDEX uq_ag_groups_tenant_farm_batch
    ON ag_groups (tenant_id, farm_id, batch_number)
    WHERE deleted_at IS NULL;

-- ── ag_weight_records (append-only) ─────────────────────────────────────
CREATE TABLE ag_weight_records (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    animal_id       UUID,
    group_id        UUID,
    recorded_date   DATE NOT NULL,
    weight_kg       NUMERIC(10,2) NOT NULL,
    sample_size     INTEGER,
    recorded_by     UUID,
    recorded_by_name VARCHAR(255),
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_ag_weight_records_target CHECK (
        (animal_id IS NOT NULL AND group_id IS NULL) OR
        (animal_id IS NULL AND group_id IS NOT NULL)
    )
);
CREATE INDEX idx_ag_weight_records_tenant ON ag_weight_records (tenant_id);
CREATE INDEX idx_ag_weight_records_animal ON ag_weight_records (animal_id);
CREATE INDEX idx_ag_weight_records_group ON ag_weight_records (group_id);

-- ── ag_health_events (mutable) ───────────────────────────────────────────
CREATE TABLE ag_health_events (
    id                        UUID PRIMARY KEY,
    tenant_id                 UUID NOT NULL,
    animal_id                 UUID,
    group_id                  UUID,
    event_type                VARCHAR(20) NOT NULL,
    event_date                DATE NOT NULL,
    description               TEXT NOT NULL,
    product_used              VARCHAR(255),
    dosage                    VARCHAR(255),
    administered_by           UUID,
    administered_by_name      VARCHAR(255),
    veterinarian              VARCHAR(255),
    cost                      NUMERIC(12,2),
    withdrawal_period_days    INTEGER,
    next_due_date             DATE,
    reminder_acknowledged     BOOLEAN NOT NULL DEFAULT FALSE,
    status                    VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    notes                     TEXT,
    created_at                TIMESTAMPTZ NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL,
    version                   BIGINT,
    CONSTRAINT chk_ag_health_events_target CHECK (
        (animal_id IS NOT NULL AND group_id IS NULL) OR
        (animal_id IS NULL AND group_id IS NOT NULL)
    )
);
CREATE INDEX idx_ag_health_events_tenant ON ag_health_events (tenant_id);
CREATE INDEX idx_ag_health_events_animal ON ag_health_events (animal_id);
CREATE INDEX idx_ag_health_events_group ON ag_health_events (group_id);
-- Backs AgHealthEventRepository.findDueAcrossTenants() — the daily sweep
-- scans every tenant's rows, so no leading tenant_id column here.
CREATE INDEX idx_ag_health_events_next_due
    ON ag_health_events (next_due_date, reminder_acknowledged)
    WHERE next_due_date IS NOT NULL;

-- ── ag_breeding_records (mutable) ────────────────────────────────────────
CREATE TABLE ag_breeding_records (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    animal_id           UUID,
    group_id            UUID,
    breeding_type       VARCHAR(10) NOT NULL,
    mating_date         DATE NOT NULL,
    sire_id             UUID,
    sire_description    VARCHAR(255),
    expected_due_date   DATE,
    actual_birth_date   DATE,
    outcome             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    offspring_count     INTEGER,
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT,
    CONSTRAINT chk_ag_breeding_records_target CHECK (
        (animal_id IS NOT NULL AND group_id IS NULL) OR
        (animal_id IS NULL AND group_id IS NOT NULL)
    )
);
CREATE INDEX idx_ag_breeding_records_tenant ON ag_breeding_records (tenant_id);
CREATE INDEX idx_ag_breeding_records_animal ON ag_breeding_records (animal_id);
CREATE INDEX idx_ag_breeding_records_group ON ag_breeding_records (group_id);

-- ── ag_movement_records (append-only) ────────────────────────────────────
CREATE TABLE ag_movement_records (
    id                     UUID PRIMARY KEY,
    tenant_id              UUID NOT NULL,
    animal_id              UUID,
    group_id               UUID,
    movement_date          DATE NOT NULL,
    movement_type          VARCHAR(20) NOT NULL,
    from_production_area_id UUID,
    to_production_area_id  UUID,
    from_farm_id           UUID,
    to_farm_id              UUID,
    count_moved            INTEGER,
    reason                 VARCHAR(255),
    notes                  TEXT,
    created_at             TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_ag_movement_records_target CHECK (
        (animal_id IS NOT NULL AND group_id IS NULL) OR
        (animal_id IS NULL AND group_id IS NOT NULL)
    )
);
CREATE INDEX idx_ag_movement_records_tenant ON ag_movement_records (tenant_id);
CREATE INDEX idx_ag_movement_records_animal ON ag_movement_records (animal_id);
CREATE INDEX idx_ag_movement_records_group ON ag_movement_records (group_id);

-- ── ag_mortality_records (append-only) ───────────────────────────────────
CREATE TABLE ag_mortality_records (
    id                     UUID PRIMARY KEY,
    tenant_id              UUID NOT NULL,
    animal_id              UUID,
    group_id               UUID,
    mortality_date         DATE NOT NULL,
    count_lost             INTEGER NOT NULL,
    cause_category         VARCHAR(20) NOT NULL,
    cause_detail           VARCHAR(255),
    estimated_value_loss   NUMERIC(12,2),
    reported_by            UUID,
    reported_by_name       VARCHAR(255),
    notes                  TEXT,
    created_at             TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_ag_mortality_records_target CHECK (
        (animal_id IS NOT NULL AND group_id IS NULL) OR
        (animal_id IS NULL AND group_id IS NOT NULL)
    )
);
CREATE INDEX idx_ag_mortality_records_tenant ON ag_mortality_records (tenant_id);
CREATE INDEX idx_ag_mortality_records_animal ON ag_mortality_records (animal_id);
CREATE INDEX idx_ag_mortality_records_group ON ag_mortality_records (group_id);

-- ── ag_feed_records (append-only) ────────────────────────────────────────
CREATE TABLE ag_feed_records (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    animal_id           UUID,
    group_id            UUID,
    feed_date           DATE NOT NULL,
    inventory_item_id   UUID,
    feed_type           VARCHAR(255) NOT NULL,
    quantity_kg         NUMERIC(12,3) NOT NULL,
    cost_per_kg         NUMERIC(12,4),
    total_cost          NUMERIC(14,2),
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_ag_feed_records_target CHECK (
        (animal_id IS NOT NULL AND group_id IS NULL) OR
        (animal_id IS NULL AND group_id IS NOT NULL)
    )
);
CREATE INDEX idx_ag_feed_records_tenant ON ag_feed_records (tenant_id);
CREATE INDEX idx_ag_feed_records_animal ON ag_feed_records (animal_id);
CREATE INDEX idx_ag_feed_records_group ON ag_feed_records (group_id);
CREATE INDEX idx_ag_feed_records_inventory_item ON ag_feed_records (inventory_item_id);

-- ── ag_inventory_items ────────────────────────────────────────────────────
CREATE TABLE ag_inventory_items (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    farm_id            UUID NOT NULL,
    item_name          VARCHAR(255) NOT NULL,
    category           VARCHAR(20) NOT NULL,
    unit_of_measure    VARCHAR(30) NOT NULL,
    current_quantity   NUMERIC(14,3) NOT NULL DEFAULT 0,
    reorder_level      NUMERIC(14,3),
    unit_cost          NUMERIC(12,2),
    supplier           VARCHAR(255),
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes              TEXT,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    deleted_at         TIMESTAMPTZ,
    version            BIGINT
);
CREATE INDEX idx_ag_inventory_items_tenant ON ag_inventory_items (tenant_id);
CREATE INDEX idx_ag_inventory_items_farm ON ag_inventory_items (farm_id);
-- Backs AgInventoryItemRepository.findBelowReorderLevelAcrossTenants() —
-- deliberately no leading tenant_id (the sweep scans every tenant).
CREATE INDEX idx_ag_inventory_items_reorder
    ON ag_inventory_items (status, deleted_at)
    WHERE reorder_level IS NOT NULL;

-- ── ag_stock_movements (append-only) ─────────────────────────────────────
CREATE TABLE ag_stock_movements (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    inventory_item_id   UUID NOT NULL,
    movement_type       VARCHAR(20) NOT NULL,
    movement_date       DATE NOT NULL,
    quantity            NUMERIC(14,3) NOT NULL,
    unit_cost           NUMERIC(12,2),
    total_cost          NUMERIC(14,2),
    reference_type      VARCHAR(50),
    reference_id        UUID,
    performed_by        UUID,
    performed_by_name   VARCHAR(255),
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_ag_stock_movements_tenant ON ag_stock_movements (tenant_id);
CREATE INDEX idx_ag_stock_movements_item ON ag_stock_movements (inventory_item_id);

-- ============================================================================
-- Module catalogue + permissions seed
-- Pattern confirmed directly against AdminLookupService.createModule() and
-- prior module migrations (Facilities/Bookkeeping status reports) — key,
-- name, description, monthly_price, icon, category, sort_order, is_active
-- on module_catalogue; permissions.name unique; role_permissions granted
-- to every tenant's ADMIN role via a NOT EXISTS guard.
-- ============================================================================

INSERT INTO module_catalogue (key, name, description, monthly_price, icon, category, sort_order, is_active)
VALUES ('agriculture', 'Agriculture', 'Farm foundation, livestock, health, breeding, feed and inventory management for agricultural operations.', 399.00, 'Wheat', 'Operations', 700, true)
ON CONFLICT (key) DO NOTHING;

INSERT INTO permissions (id, name, description)
VALUES
    (gen_random_uuid(), 'AGRICULTURE_READ', 'View Agriculture data'),
    (gen_random_uuid(), 'AGRICULTURE_MANAGE', 'Create and manage Agriculture records'),
    (gen_random_uuid(), 'AGRICULTURE_ADMIN', 'Full administrative access to Agriculture')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('AGRICULTURE_READ', 'AGRICULTURE_MANAGE', 'AGRICULTURE_ADMIN')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
