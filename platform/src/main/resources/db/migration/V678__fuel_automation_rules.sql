-- Phase 6b: automation. A rule the company sets says: when this tank falls below this many litres, draft a requisition for this
-- many litres. The job only ever creates a DRAFT requisition: nothing is submitted, approved or ordered by it.
CREATE TABLE fuel_automation_rules (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    tank_id               UUID NOT NULL REFERENCES fuel_tanks(id),
    supplier_id           UUID REFERENCES fuel_suppliers(id),
    reorder_below_litres  NUMERIC(14,2) NOT NULL,
    order_litres          NUMERIC(14,2) NOT NULL,
    estimated_price       NUMERIC(12,4),
    enabled               BOOLEAN NOT NULL DEFAULT TRUE,
    last_checked_at       TIMESTAMPTZ,
    last_drafted_at       TIMESTAMPTZ,
    created_by            UUID,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at            TIMESTAMPTZ,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_fuel_automation_level CHECK (reorder_below_litres > 0),
    CONSTRAINT ck_fuel_automation_qty CHECK (order_litres > 0),
    CONSTRAINT ck_fuel_automation_price CHECK (estimated_price IS NULL OR estimated_price >= 0)
);
CREATE UNIQUE INDEX uq_fuel_automation_rule_tank ON fuel_automation_rules (tenant_id, tank_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_fuel_automation_rules_enabled ON fuel_automation_rules (tenant_id) WHERE deleted_at IS NULL AND enabled;

-- What each run did, so a person can see why a draft appeared (or why a rule failed). Append-only.
CREATE TABLE fuel_automation_log (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    rule_id         UUID NOT NULL REFERENCES fuel_automation_rules(id),
    outcome         VARCHAR(12) NOT NULL,
    detail          VARCHAR(300),
    requisition_id  UUID,
    ran_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_fuel_automation_log_outcome CHECK (outcome IN ('DRAFTED','FAILED'))
);
CREATE INDEX idx_fuel_automation_log_tenant ON fuel_automation_log (tenant_id, ran_at DESC);

-- A requisition drafted by a rule remembers which rule.
ALTER TABLE fuel_purchase_requisitions ADD COLUMN automation_rule_id UUID;

-- Overdue purchase order alerts are opt-in; each order is alerted once.
ALTER TABLE fuel_settings ADD COLUMN alert_overdue_purchase_orders BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE fuel_purchase_orders ADD COLUMN overdue_alerted_at TIMESTAMPTZ;
