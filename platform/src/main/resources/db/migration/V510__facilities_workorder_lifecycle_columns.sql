-- Internal Facilities: the work-order lifecycle (request -> approval -> work -> verification) and cost lines.
--
-- V505 was meant to carry this but is a byte-for-byte copy of V504 (site scope and audit), so the columns that
-- FacilityWorkOrder maps and the facility_work_order_cost_lines table were never created: with
-- spring.jpa.hibernate.ddl-auto=validate the application would not start. V505 is left as it is (it is idempotent
-- and may already be applied); this migration adds what was missing. Everything is IF NOT EXISTS, so it is safe
-- on a database where the columns were added by hand.
--
-- New status values on facility_work_orders.status: REQUESTED, REJECTED, PENDING_VERIFICATION (the column has no
-- CHECK constraint).

ALTER TABLE facility_work_orders
    ADD COLUMN IF NOT EXISTS requested_by_user_id        UUID,
    ADD COLUMN IF NOT EXISTS safety_impact               BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS duplicate_of_work_order_id  UUID,
    ADD COLUMN IF NOT EXISTS approved_by_user_id         UUID,
    ADD COLUMN IF NOT EXISTS approved_at                 TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rejection_reason            VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS completed_by_user_id        UUID,
    ADD COLUMN IF NOT EXISTS requires_verification       BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS verified_by_user_id         UUID,
    ADD COLUMN IF NOT EXISTS verified_at                 TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rework_count                INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_rework_reason          VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS downtime_minutes            INTEGER,
    ADD COLUMN IF NOT EXISTS failure_cause               VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_facility_wo_tenant_status ON facility_work_orders (tenant_id, status);

CREATE TABLE IF NOT EXISTS facility_work_order_cost_lines (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID           NOT NULL,
    work_order_id       UUID           NOT NULL,
    cost_type           VARCHAR(20)    NOT NULL,
    description         VARCHAR(255)   NOT NULL,
    quantity            NUMERIC(12, 3) NOT NULL,
    unit_cost           NUMERIC(15, 2) NOT NULL,
    total_cost          NUMERIC(15, 2) NOT NULL,
    recorded_by_user_id UUID,
    recorded_at         TIMESTAMPTZ    NOT NULL,
    CONSTRAINT fk_facility_cost_line_wo FOREIGN KEY (work_order_id) REFERENCES facility_work_orders (id),
    CONSTRAINT chk_facility_cost_line_amounts CHECK (quantity > 0 AND unit_cost >= 0)
);
CREATE INDEX IF NOT EXISTS idx_facility_cost_lines_wo ON facility_work_order_cost_lines (tenant_id, work_order_id);
