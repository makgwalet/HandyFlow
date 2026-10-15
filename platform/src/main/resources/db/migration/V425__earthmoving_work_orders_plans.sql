-- V425 (Earthmoving patch E2, 2026-10-15): work orders, maintenance plans, and the link to release.
--
-- Before this, maintenance was a log of work already done: nobody could request, assign, schedule or track a job, and a
-- machine could be released while repair work was still open.
--   earthmoving_counters         per-tenant running numbers (WO-0001, ...)
--   earthmoving_work_orders      request -> in progress / awaiting parts -> completed or cancelled. Completing one writes the
--                                maintenance record (cost, hours, supplier) and, for a plan, moves the plan on.
--   earthmoving_work_order_lines parts, labour and other costs on a work order
--   earthmoving_maintenance_plans  recurring tasks per machine, by operating hours or by days. A daily check raises a work order
--                                when one is due and none is already open.
--   earthmoving_status_events.work_order_id  the work order a release rests on (patch E1 table)
-- A work order that blocks release (the default) stops the machine being released from maintenance or breakdown until it is
-- completed or cancelled. Re-runnable.

CREATE TABLE IF NOT EXISTS earthmoving_counters (
    tenant_id  UUID        NOT NULL,
    name       VARCHAR(30) NOT NULL,
    next_value BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, name)
);

CREATE TABLE IF NOT EXISTS earthmoving_maintenance_plans (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    asset_id        UUID NOT NULL REFERENCES earthmoving_assets(id),
    name            VARCHAR(200) NOT NULL,
    task_type       VARCHAR(30)  NOT NULL,
    trigger_type    VARCHAR(10)  NOT NULL CHECK (trigger_type IN ('HOURS', 'DAYS')),
    interval_value  INTEGER      NOT NULL CHECK (interval_value > 0),
    lead_value      INTEGER      NOT NULL DEFAULT 0 CHECK (lead_value >= 0),
    last_done_hours NUMERIC(10,1),
    last_done_date  DATE,
    priority        VARCHAR(10)  NOT NULL DEFAULT 'NORMAL' CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    blocks_release  BOOLEAN      NOT NULL DEFAULT TRUE,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_em_plan_type CHECK (task_type IN
        ('SERVICE','REPAIR','INSPECTION','TYRE','BATTERY','ELECTRICAL','HYDRAULICS','ENGINE','TRACKS','OTHER'))
);
CREATE INDEX IF NOT EXISTS idx_em_plans_tenant ON earthmoving_maintenance_plans (tenant_id, active);
CREATE INDEX IF NOT EXISTS idx_em_plans_asset  ON earthmoving_maintenance_plans (asset_id);

CREATE TABLE IF NOT EXISTS earthmoving_work_orders (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL,
    asset_id              UUID NOT NULL REFERENCES earthmoving_assets(id),
    wo_number             VARCHAR(20)  NOT NULL,
    title                 VARCHAR(255) NOT NULL,
    description           TEXT,
    type                  VARCHAR(30)  NOT NULL,
    priority              VARCHAR(10)  NOT NULL DEFAULT 'NORMAL',
    status                VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    assigned_to_user_id   UUID,
    assigned_to_name      VARCHAR(200),
    due_date              DATE,
    source                VARCHAR(10)  NOT NULL DEFAULT 'MANUAL',
    plan_id               UUID REFERENCES earthmoving_maintenance_plans(id),
    incident_id           UUID REFERENCES earthmoving_incidents(id),
    blocks_release        BOOLEAN      NOT NULL DEFAULT TRUE,
    requested_by          UUID,
    started_at            TIMESTAMP,
    completed_at          TIMESTAMP,
    completed_by          UUID,
    meter_hours           NUMERIC(10,1),
    downtime_hours        NUMERIC(10,1),
    labour_hours          NUMERIC(10,1),
    work_done             TEXT,
    test_result           TEXT,
    maintenance_record_id UUID,
    cancel_reason         TEXT,
    created_at            TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT now(),
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_em_wo_number UNIQUE (tenant_id, wo_number),
    CONSTRAINT chk_em_wo_status   CHECK (status IN ('OPEN','IN_PROGRESS','AWAITING_PARTS','COMPLETED','CANCELLED')),
    CONSTRAINT chk_em_wo_priority CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
    CONSTRAINT chk_em_wo_source   CHECK (source IN ('MANUAL','PLAN','INCIDENT')),
    CONSTRAINT chk_em_wo_type     CHECK (type IN
        ('SERVICE','REPAIR','INSPECTION','TYRE','BATTERY','ELECTRICAL','HYDRAULICS','ENGINE','TRACKS','OTHER')),
    CONSTRAINT chk_em_wo_hours    CHECK ((downtime_hours IS NULL OR downtime_hours >= 0) AND (labour_hours IS NULL OR labour_hours >= 0)
                                         AND (meter_hours IS NULL OR meter_hours >= 0))
);
CREATE INDEX IF NOT EXISTS idx_em_wo_tenant_status ON earthmoving_work_orders (tenant_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_em_wo_asset         ON earthmoving_work_orders (asset_id, status);
-- at most one open work order per plan, so a plan that is due cannot raise the same job twice
CREATE UNIQUE INDEX IF NOT EXISTS uq_em_wo_one_open_per_plan ON earthmoving_work_orders (plan_id)
    WHERE plan_id IS NOT NULL AND status NOT IN ('COMPLETED', 'CANCELLED');

CREATE TABLE IF NOT EXISTS earthmoving_work_order_lines (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL,
    work_order_id UUID NOT NULL REFERENCES earthmoving_work_orders(id) ON DELETE CASCADE,
    kind          VARCHAR(10)  NOT NULL CHECK (kind IN ('PART', 'LABOUR', 'OTHER')),
    description   VARCHAR(255) NOT NULL,
    quantity      NUMERIC(12,2) NOT NULL CHECK (quantity > 0),
    unit_cost     NUMERIC(15,2) NOT NULL CHECK (unit_cost >= 0),
    supplier      VARCHAR(255),
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_em_wo_lines ON earthmoving_work_order_lines (work_order_id);

ALTER TABLE earthmoving_status_events ADD COLUMN IF NOT EXISTS work_order_id UUID;
