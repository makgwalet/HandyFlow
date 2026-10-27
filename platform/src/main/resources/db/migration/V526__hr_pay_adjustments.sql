-- Payroll: bonuses and back pay. An item says what is owed to one employee; the next pay run whose period ends on or after
-- pay_from takes it (pay_run_id is set), and a cancelled or recalculated run gives it back. Items are never deleted or edited:
-- a wrong one is cancelled with a reason and a new one is entered.

CREATE TABLE IF NOT EXISTS hr_pay_adjustments (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID          NOT NULL REFERENCES tenants(id),
    employee_id    UUID          NOT NULL REFERENCES hr_employees(id),
    kind           VARCHAR(16)   NOT NULL CHECK (kind IN ('BONUS','BACK_PAY')),
    amount         NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    pay_from       DATE          NOT NULL,
    description    VARCHAR(200)  NOT NULL,
    relates_from   DATE,
    relates_to     DATE,
    reason         VARCHAR(300)  NOT NULL,
    status         VARCHAR(12)   NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CANCELLED')),
    pay_run_id     UUID REFERENCES hr_pay_runs(id),
    created_by     UUID,
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    cancelled_by   UUID,
    cancelled_at   TIMESTAMP,
    cancel_reason  VARCHAR(300),
    CHECK (relates_to IS NULL OR relates_from IS NULL OR relates_to >= relates_from)
);
CREATE INDEX IF NOT EXISTS idx_hr_pay_adjustments_open ON hr_pay_adjustments (tenant_id, status, pay_from);
CREATE INDEX IF NOT EXISTS idx_hr_pay_adjustments_employee ON hr_pay_adjustments (tenant_id, employee_id, created_at);
CREATE INDEX IF NOT EXISTS idx_hr_pay_adjustments_run ON hr_pay_adjustments (pay_run_id) WHERE pay_run_id IS NOT NULL;

ALTER TABLE hr_payslips
    ADD COLUMN IF NOT EXISTS back_pay_amount NUMERIC(12,2) NOT NULL DEFAULT 0;
