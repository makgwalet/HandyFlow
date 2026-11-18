-- Supply Chain: procurement budgets, the commitments purchase orders place against them, and approved overrides.

CREATE TABLE IF NOT EXISTS sc_budgets (
    id            UUID PRIMARY KEY,
    tenant_id     UUID          NOT NULL,
    name          VARCHAR(200)  NOT NULL,
    scope_type    VARCHAR(20)   NOT NULL,
    scope_value   VARCHAR(100)  NOT NULL,
    period_start  DATE          NOT NULL,
    period_end    DATE          NOT NULL,
    amount        NUMERIC(15,2) NOT NULL,
    control_mode  VARCHAR(10)   NOT NULL,
    status        VARCHAR(10)   NOT NULL,
    currency      VARCHAR(3)    NOT NULL,
    created_by    UUID,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_sc_budget_scope   CHECK (scope_type IN ('DEPARTMENT','COST_CENTRE','PROJECT')),
    CONSTRAINT chk_sc_budget_mode    CHECK (control_mode IN ('NONE','WARN','HARD_STOP')),
    CONSTRAINT chk_sc_budget_status  CHECK (status IN ('ACTIVE','CLOSED')),
    CONSTRAINT chk_sc_budget_period  CHECK (period_end >= period_start),
    CONSTRAINT chk_sc_budget_amount  CHECK (amount >= 0)
);
CREATE INDEX IF NOT EXISTS idx_sc_budgets_lookup ON sc_budgets(tenant_id, status, scope_type, lower(scope_value), period_start, period_end);

-- One commitment per purchase order (re-committed in place when the PO is amended).
CREATE TABLE IF NOT EXISTS sc_budget_commitments (
    id                UUID PRIMARY KEY,
    tenant_id         UUID          NOT NULL,
    purchase_order_id UUID          NOT NULL REFERENCES sc_purchase_orders(id) ON DELETE CASCADE,
    amount            NUMERIC(15,2) NOT NULL,
    department        VARCHAR(100),
    cost_centre       VARCHAR(100),
    project_ref       VARCHAR(100),
    spend_date        DATE          NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_sc_budget_commitment_po UNIQUE (purchase_order_id),
    CONSTRAINT chk_sc_budget_commitment_amount CHECK (amount >= 0)
);
CREATE INDEX IF NOT EXISTS idx_sc_budget_commitments_spend ON sc_budget_commitments(tenant_id, spend_date);

CREATE TABLE IF NOT EXISTS sc_budget_events (
    id         UUID PRIMARY KEY,
    tenant_id  UUID         NOT NULL,
    budget_id  UUID,
    event_type VARCHAR(30)  NOT NULL,
    detail     VARCHAR(1000),
    actor      UUID,
    actor_name VARCHAR(255),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_sc_budget_events ON sc_budget_events(tenant_id, budget_id, created_at DESC);

CREATE TABLE IF NOT EXISTS sc_budget_overrides (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID          NOT NULL,
    entity_type        VARCHAR(20)   NOT NULL,
    entity_id          UUID          NOT NULL,
    amount             NUMERIC(15,2) NOT NULL,
    reason             VARCHAR(500)  NOT NULL,
    overridden_by      UUID,
    overridden_by_name VARCHAR(255),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_sc_budget_override_type CHECK (entity_type IN ('REQUISITION','PURCHASE_ORDER'))
);
CREATE INDEX IF NOT EXISTS idx_sc_budget_overrides_entity ON sc_budget_overrides(tenant_id, entity_type, entity_id);
