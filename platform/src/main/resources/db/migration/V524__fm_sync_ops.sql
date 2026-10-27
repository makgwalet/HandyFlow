-- Idempotency record for operations uploaded from FM field devices.
CREATE TABLE IF NOT EXISTS fm_sync_ops (
    id         UUID PRIMARY KEY,
    tenant_id  UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    op_id      UUID        NOT NULL,
    op_type    VARCHAR(40) NOT NULL,
    target_id  UUID        NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_fm_sync_op UNIQUE (tenant_id, user_id, op_id)
);
CREATE INDEX IF NOT EXISTS idx_fm_sync_ops_applied ON fm_sync_ops (applied_at);

-- Work-order change feed for offline sync.
CREATE INDEX IF NOT EXISTS idx_fm_wo_tenant_updated ON fm_work_orders (tenant_id, updated_at);
