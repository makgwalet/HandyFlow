-- Compliance Services: what an hour of each person costs the practice. Only administrators see or set these. A rate is never changed:
-- a new rate with a later date takes over. Hours worked before any rate exists stay unpriced rather than guessed.

CREATE TABLE IF NOT EXISTS compliance_cost_rates (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    user_id        UUID NOT NULL,
    hourly_cost    NUMERIC(12,2) NOT NULL,
    effective_from DATE NOT NULL,
    set_by         UUID NOT NULL,
    set_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_compliance_cost_rate_amount CHECK (hourly_cost > 0 AND hourly_cost <= 10000),
    CONSTRAINT uq_compliance_cost_rate UNIQUE (tenant_id, user_id, effective_from)
);
CREATE INDEX IF NOT EXISTS idx_compliance_cost_rate_user ON compliance_cost_rates(tenant_id, user_id, effective_from);
