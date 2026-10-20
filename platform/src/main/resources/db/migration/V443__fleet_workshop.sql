-- Rename to the next free Flyway version, e.g. V129__fleet_workshop.sql

CREATE TABLE fleet_work_order_counters (
    tenant_id   UUID    NOT NULL,
    year        INTEGER NOT NULL,
    last_number INTEGER NOT NULL,
    PRIMARY KEY (tenant_id, year)
);

CREATE TABLE fleet_technicians (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    name        VARCHAR(255) NOT NULL,
    phone       VARCHAR(50),
    email       VARCHAR(255),
    hourly_rate NUMERIC(15, 2),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_fleet_technicians_tenant ON fleet_technicians (tenant_id, active);

CREATE TABLE fleet_work_orders (
    id                       UUID PRIMARY KEY,
    tenant_id                UUID         NOT NULL,
    reference_number         VARCHAR(30)  NOT NULL,
    vehicle_id               UUID         NOT NULL REFERENCES fleet_vehicles (id),
    issue_id                 UUID         REFERENCES fleet_issues (id),
    accident_id              UUID         REFERENCES fleet_accidents (id),
    work_type                VARCHAR(30)  NOT NULL,
    title                    VARCHAR(255) NOT NULL,
    description              TEXT,
    priority                 VARCHAR(10)  NOT NULL DEFAULT 'NORMAL',
    status                   VARCHAR(20)  NOT NULL,   -- OPEN | QUOTED | APPROVED | IN_PROGRESS | ON_HOLD | COMPLETED | CANCELLED
    assigned_technician_id   UUID         REFERENCES fleet_technicians (id),
    supplier                 VARCHAR(255),            -- external workshop, if not done in-house
    scheduled_for            DATE,
    odometer_in              INTEGER,
    vehicle_off_road_from    TIMESTAMPTZ,
    started_at               TIMESTAMPTZ,
    completed_at             TIMESTAMPTZ,

    quoted_amount            NUMERIC(15, 2),
    quotation_ref            VARCHAR(100),
    quotation_date           DATE,
    quotation_valid_until    DATE,
    quotation_notes          TEXT,

    approved_by              UUID,
    approved_at              TIMESTAMPTZ,
    approval_note            TEXT,
    rejection_reason         TEXT,
    hold_reason              TEXT,
    cancel_reason            TEXT,

    completion_odometer      INTEGER,
    invoice_ref              VARCHAR(100),
    completion_notes         TEXT,
    next_service_km          INTEGER,
    service_record_id        UUID,                    -- the VehicleService history row written on completion

    requested_by             UUID,
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL,
    version                  BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_fleet_work_orders_reference UNIQUE (tenant_id, reference_number)
);
CREATE INDEX idx_fleet_work_orders_tenant ON fleet_work_orders (tenant_id, created_at DESC);
CREATE INDEX idx_fleet_work_orders_vehicle ON fleet_work_orders (tenant_id, vehicle_id, created_at DESC);
CREATE INDEX idx_fleet_work_orders_status ON fleet_work_orders (tenant_id, status);
CREATE INDEX idx_fleet_work_orders_issue ON fleet_work_orders (tenant_id, issue_id) WHERE issue_id IS NOT NULL;

CREATE TABLE fleet_work_order_lines (
    id            UUID PRIMARY KEY,
    work_order_id UUID           NOT NULL REFERENCES fleet_work_orders (id) ON DELETE CASCADE,
    line_type     VARCHAR(10)    NOT NULL,   -- LABOUR | PART | SUBLET | OTHER
    description   VARCHAR(500)   NOT NULL,
    part_number   VARCHAR(100),
    quantity      NUMERIC(10, 2) NOT NULL,   -- hours for labour
    unit_cost     NUMERIC(15, 2) NOT NULL,
    technician_id UUID           REFERENCES fleet_technicians (id),
    created_at    TIMESTAMPTZ    NOT NULL,
    CONSTRAINT chk_fleet_work_order_lines_positive CHECK (quantity > 0 AND unit_cost >= 0)
);
CREATE INDEX idx_fleet_work_order_lines_wo ON fleet_work_order_lines (work_order_id);

CREATE TABLE fleet_work_order_attachments (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    work_order_id   UUID         NOT NULL REFERENCES fleet_work_orders (id),
    attachment_type VARCHAR(20)  NOT NULL,
    description     VARCHAR(500),
    file_name       VARCHAR(255) NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    storage_key     VARCHAR(500) NOT NULL,
    uploaded_by     UUID,
    uploaded_at     TIMESTAMPTZ  NOT NULL,
    deleted_at      TIMESTAMPTZ,
    deleted_by      UUID
);
CREATE INDEX idx_fleet_work_order_attachments_wo
    ON fleet_work_order_attachments (tenant_id, work_order_id) WHERE deleted_at IS NULL;