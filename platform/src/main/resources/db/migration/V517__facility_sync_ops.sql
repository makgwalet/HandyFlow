-- Idempotency record for operations uploaded from field devices.
CREATE TABLE IF NOT EXISTS facility_sync_ops (
    id         UUID PRIMARY KEY,
    tenant_id  UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    op_id      UUID        NOT NULL,
    op_type    VARCHAR(40) NOT NULL,
    target_id  UUID        NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_facility_sync_op UNIQUE (tenant_id, user_id, op_id)
);
CREATE INDEX IF NOT EXISTS idx_facility_sync_ops_applied ON facility_sync_ops (applied_at);

-- Work-order change feed for offline sync.
CREATE INDEX IF NOT EXISTS idx_facility_wo_tenant_updated ON facility_work_orders (tenant_id, updated_at);
