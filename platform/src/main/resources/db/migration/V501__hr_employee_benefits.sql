-- Payroll: what the employer pays towards an employee's medical aid and retirement fund, and other taxable fringe benefits.
-- These are taxable benefits, not cash: they raise the income PAYE is worked out on and are reported on the tax certificate.
-- A line has a start date and an optional end date; a change is a new line, so what was true for a past payslip stays on record.

CREATE TABLE IF NOT EXISTS hr_employee_benefits (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID          NOT NULL REFERENCES tenants(id),
    employee_id    UUID          NOT NULL REFERENCES hr_employees(id),
    kind           VARCHAR(24)   NOT NULL CHECK (kind IN ('EMPLOYER_MEDICAL','EMPLOYER_RETIREMENT','OTHER_FRINGE')),
    monthly_amount NUMERIC(12,2) NOT NULL CHECK (monthly_amount > 0),
    effective_from DATE          NOT NULL,
    effective_to   DATE,
    description    VARCHAR(200),
    reason         VARCHAR(300)  NOT NULL,
    created_by     UUID,
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    ended_by       UUID,
    ended_at       TIMESTAMP,
    CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
CREATE INDEX IF NOT EXISTS idx_hr_employee_benefits_employee ON hr_employee_benefits (tenant_id, employee_id, kind, effective_from);

ALTER TABLE hr_payslips
    ADD COLUMN IF NOT EXISTS employer_medical    NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS employer_retirement NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS other_fringe        NUMERIC(12,2) NOT NULL DEFAULT 0;
