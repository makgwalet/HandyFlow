-- Finance F23: configurable collection strategies. A strategy picks cases by balance and days overdue and lists steps;
-- each step raises one work item per case. Applications record what was raised or skipped so nothing repeats.
CREATE TABLE IF NOT EXISTS dc_strategies (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    name          VARCHAR(100) NOT NULL,
    description   VARCHAR(500),
    priority      INT NOT NULL CHECK (priority BETWEEN 1 AND 999),
    min_balance   NUMERIC(15,2) CHECK (min_balance IS NULL OR min_balance >= 0),
    max_balance   NUMERIC(15,2),
    min_days      INT CHECK (min_days IS NULL OR min_days >= 0),
    max_days      INT,
    quiet_days    INT NOT NULL DEFAULT 3 CHECK (quiet_days BETWEEN 0 AND 30),
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_by    UUID,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_dc_strategies_tenant ON dc_strategies (tenant_id, active, priority);

CREATE TABLE IF NOT EXISTS dc_strategy_steps (
    id            UUID PRIMARY KEY,
    strategy_id   UUID NOT NULL REFERENCES dc_strategies (id) ON DELETE CASCADE,
    tenant_id     UUID NOT NULL,
    after_days    INT NOT NULL CHECK (after_days BETWEEN 0 AND 730),
    action        VARCHAR(20) NOT NULL CHECK (action IN ('CALL', 'SEND_REMINDER', 'SEND_DEMAND', 'LEGAL_REVIEW', 'REVIEW_WRITE_OFF', 'OTHER')),
    instruction   VARCHAR(300),
    CONSTRAINT uq_dc_step_day UNIQUE (strategy_id, after_days)
);

CREATE TABLE IF NOT EXISTS dc_strategy_applications (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    case_id        UUID NOT NULL,
    strategy_id    UUID NOT NULL,
    step_id        UUID NOT NULL,
    outcome        VARCHAR(20) NOT NULL CHECK (outcome IN ('RAISED', 'SKIPPED_LATE')),
    work_item_id   UUID,
    days_overdue   INT,
    applied_on     DATE NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_dc_strategy_application UNIQUE (case_id, step_id)
);
CREATE INDEX IF NOT EXISTS idx_dc_strategy_app_case ON dc_strategy_applications (tenant_id, case_id);
