-- Agriculture chunk 5: every change to a group's head count is logged with who, when, before, after and why.
CREATE TABLE IF NOT EXISTS ag_group_count_log (
    id               UUID PRIMARY KEY,
    tenant_id        UUID        NOT NULL,
    group_id         UUID        NOT NULL,
    change_type      VARCHAR(20) NOT NULL,
    delta            INTEGER     NOT NULL,
    count_before     INTEGER     NOT NULL,
    count_after      INTEGER     NOT NULL,
    reason           TEXT        NOT NULL,
    source_type      VARCHAR(50),
    source_id        UUID,
    created_by       UUID,
    created_by_name  VARCHAR(255),
    created_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_ag_group_count_log_type CHECK (change_type IN ('INCREASE', 'DECREASE', 'SALE', 'TRANSFER_OUT', 'MORTALITY')),
    CONSTRAINT chk_ag_group_count_log_counts CHECK (count_before >= 0 AND count_after >= 0 AND delta = count_after - count_before),
    CONSTRAINT chk_ag_group_count_log_reason CHECK (length(btrim(reason)) > 0)
);
CREATE INDEX IF NOT EXISTS idx_ag_group_count_log_group ON ag_group_count_log (tenant_id, group_id, created_at DESC);

