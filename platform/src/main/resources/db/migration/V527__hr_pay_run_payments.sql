-- Payroll: the bank payment file for a finalised pay run. One row per employee paid. The bank details are copied at the time the
-- payments are made up, so a later change of bank details does not rewrite what was sent. A payment is PENDING until the file has
-- been given to the bank and payroll confirms it, PAID once confirmed, and RETURNED when the bank bounced it.

CREATE TABLE IF NOT EXISTS hr_pay_run_payments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID          NOT NULL REFERENCES tenants(id),
    pay_run_id       UUID          NOT NULL REFERENCES hr_pay_runs(id),
    payslip_id       UUID          NOT NULL REFERENCES hr_payslips(id),
    employee_id      UUID          NOT NULL REFERENCES hr_employees(id),
    amount           NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    beneficiary_name VARCHAR(120)  NOT NULL,
    bank_name        VARCHAR(80),
    branch_code      VARCHAR(10)   NOT NULL,
    account_number   VARCHAR(20)   NOT NULL,
    reference        VARCHAR(20)   NOT NULL,
    status           VARCHAR(10)   NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PAID','RETURNED')),
    exported_at      TIMESTAMP,
    paid_at          TIMESTAMP,
    paid_by          UUID,
    returned_at      TIMESTAMP,
    returned_by      UUID,
    return_reason    VARCHAR(300),
    created_by       UUID,
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hr_pay_run_payment_slip UNIQUE (payslip_id)
);
CREATE INDEX IF NOT EXISTS idx_hr_pay_run_payments_run ON hr_pay_run_payments (tenant_id, pay_run_id);
