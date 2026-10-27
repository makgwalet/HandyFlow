-- Chunk 6: Facilities Management work-order lifecycle (request -> approval -> work -> verification) and cost capture.
-- RENAME to the next free Flyway version; apply AFTER the Chunk 2 and Chunk 5 migrations.
--
-- NEW STATUS VALUES on fm_work_orders.status: REQUESTED, REJECTED, PENDING_VERIFICATION.
-- If that column has a CHECK constraint in your schema, extend it, e.g.:
--   ALTER TABLE fm_work_orders DROP CONSTRAINT <name>;
--   ALTER TABLE fm_work_orders ADD CONSTRAINT <name> CHECK (status IN
--     ('REQUESTED','REJECTED','OPEN','ASSIGNED','IN_PROGRESS','ON_HOLD','PENDING_VERIFICATION','COMPLETED','CANCELLED'));

ALTER TABLE fm_work_orders
    ADD COLUMN IF NOT EXISTS requested_by_user_id        UUID,
    ADD COLUMN IF NOT EXISTS safety_impact               BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS duplicate_of_work_order_id  UUID,
    ADD COLUMN IF NOT EXISTS approved_by_user_id         UUID,
    ADD COLUMN IF NOT EXISTS approved_at                 TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rejection_reason            VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS requires_verification       BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS completed_by_user_id        UUID,
    ADD COLUMN IF NOT EXISTS verified_by_user_id         UUID,
    ADD COLUMN IF NOT EXISTS verified_at                 TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rework_count                INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_rework_reason          VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS downtime_minutes            INTEGER,
    ADD COLUMN IF NOT EXISTS failure_cause               VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_fm_wo_tenant_status ON fm_work_orders (tenant_id, status);

-- Itemised cost lines (labour, parts, travel, contractor, other). A line is billable by default.
-- On completion the work order's cost (the amount billed in time-and-materials invoicing) becomes the sum
-- of its BILLABLE lines when any lines exist; non-billable lines are kept for job-costing only.
CREATE TABLE IF NOT EXISTS fm_work_order_cost_lines (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID           NOT NULL,
    work_order_id       UUID           NOT NULL,
    cost_type           VARCHAR(20)    NOT NULL,
    description         VARCHAR(255)   NOT NULL,
    quantity            NUMERIC(12, 3) NOT NULL,
    unit_cost           NUMERIC(15, 2) NOT NULL,
    total_cost          NUMERIC(15, 2) NOT NULL,
    billable            BOOLEAN        NOT NULL DEFAULT TRUE,
    recorded_by_user_id UUID,
    recorded_at         TIMESTAMPTZ    NOT NULL,
    CONSTRAINT fk_fm_cost_line_wo FOREIGN KEY (work_order_id) REFERENCES fm_work_orders (id),
    CONSTRAINT chk_fm_cost_line_amounts CHECK (quantity > 0 AND unit_cost >= 0)
);
CREATE INDEX IF NOT EXISTS idx_fm_cost_lines_wo ON fm_work_order_cost_lines (tenant_id, work_order_id);