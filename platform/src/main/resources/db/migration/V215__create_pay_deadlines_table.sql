CREATE TABLE pay_deadlines (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID NOT NULL,
    pay_client_id      UUID NOT NULL REFERENCES pay_clients(id),
    deadline_type      VARCHAR(20) NOT NULL,
    period_year        INTEGER NOT NULL,
    period_month       INTEGER,
    raw_due_date       DATE NOT NULL,
    adjusted_due_date  DATE NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    filed_date         DATE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_pay_deadline UNIQUE (pay_client_id, deadline_type, period_year, period_month)
);
CREATE INDEX idx_pay_deadlines_client ON pay_deadlines (pay_client_id);