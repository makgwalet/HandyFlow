-- Finance F21: budgets by account and month, and manual items for the cash forecast.
CREATE TABLE IF NOT EXISTS acc_budgets (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    name          VARCHAR(120) NOT NULL,
    start_date    DATE NOT NULL,
    status        VARCHAR(10) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE', 'CLOSED')),
    created_by    UUID,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    activated_by  UUID,
    activated_at  TIMESTAMPTZ,
    closed_at     TIMESTAMPTZ,
    CONSTRAINT acc_budgets_first_of_month CHECK (EXTRACT(DAY FROM start_date) = 1)
);
CREATE INDEX IF NOT EXISTS idx_acc_budgets_tenant ON acc_budgets (tenant_id, start_date DESC);
-- One active budget per tenant and start month.
CREATE UNIQUE INDEX IF NOT EXISTS uq_acc_budgets_active ON acc_budgets (tenant_id, start_date) WHERE status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS acc_budget_lines (
    budget_id   UUID NOT NULL REFERENCES acc_budgets (id) ON DELETE CASCADE,
    tenant_id   UUID NOT NULL,
    account_id  UUID NOT NULL,
    month_index SMALLINT NOT NULL CHECK (month_index BETWEEN 0 AND 11),
    amount      NUMERIC(15,2) NOT NULL CHECK (amount >= 0),
    PRIMARY KEY (budget_id, account_id, month_index)
);

CREATE TABLE IF NOT EXISTS acc_forecast_items (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    item_date   DATE NOT NULL,
    amount      NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    direction   VARCHAR(3) NOT NULL CHECK (direction IN ('IN', 'OUT')),
    label       VARCHAR(200) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_acc_forecast_items_tenant ON acc_forecast_items (tenant_id, item_date) WHERE active;
