-- V951: pay groups (phase 1, slice 1c). A client can have several groups, each with its own pay
-- frequency and period calendar (monthly, fortnightly, weekly). Employees and pay runs may
-- belong to a group; NULL means the client's default (ungrouped) payroll, which behaves as before.
CREATE TABLE pay_groups (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    pay_client_id   UUID NOT NULL REFERENCES pay_clients(id),
    name            VARCHAR(100) NOT NULL,
    frequency       VARCHAR(12) NOT NULL CHECK (frequency IN ('MONTHLY','FORTNIGHTLY','WEEKLY')),
    anchor_date     DATE,
    pay_lag_days    INTEGER NOT NULL DEFAULT 0 CHECK (pay_lag_days BETWEEN 0 AND 31),
    status          VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    CHECK (frequency = 'MONTHLY' OR anchor_date IS NOT NULL)
);
CREATE UNIQUE INDEX uq_pay_groups_client_name ON pay_groups (pay_client_id, lower(name)) WHERE status = 'ACTIVE';
CREATE INDEX idx_pay_groups_client ON pay_groups (tenant_id, pay_client_id);

ALTER TABLE pay_employees ADD COLUMN pay_group_id UUID REFERENCES pay_groups(id);
ALTER TABLE pay_runs      ADD COLUMN pay_group_id UUID REFERENCES pay_groups(id);
CREATE INDEX idx_pay_employees_group ON pay_employees (pay_group_id);
CREATE INDEX idx_pay_runs_group ON pay_runs (pay_group_id);
