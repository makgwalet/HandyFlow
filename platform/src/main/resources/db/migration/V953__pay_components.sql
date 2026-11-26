-- V953: pay components (phase 1, slice 1e).
-- pay_components: a per-tenant catalogue. role decides how the calculator treats an amount:
--   TAXABLE_EARNING      regular pay added to the period's taxable pay (overtime, commission, allowances); liable for UIF and SDL
--   BONUS                irregular pay taxed by the bonus method; liable for UIF and SDL
--   NON_TAXABLE_EARNING  paid but not taxed, no UIF/SDL (expense reimbursements)
--   DEDUCTION            taken from net pay after tax (loan repayment, garnishee, union fees)
-- pay_employee_components: amounts an employee gets (or has taken) every pay period.
-- pay_run_items: amounts for one employee in one DRAFT pay run only.
-- pay_payslip_lines: what each payslip actually used, copied at processing so later catalogue edits cannot change a past payslip.
CREATE TABLE pay_components (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    code        VARCHAR(30) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    role        VARCHAR(20) NOT NULL CHECK (role IN ('TAXABLE_EARNING','BONUS','NON_TAXABLE_EARNING','DEDUCTION')),
    sars_code   VARCHAR(8),
    standard    BOOLEAN NOT NULL DEFAULT FALSE,
    status      VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_pay_components_code ON pay_components (tenant_id, upper(code));

CREATE TABLE pay_employee_components (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    pay_employee_id UUID NOT NULL REFERENCES pay_employees(id),
    component_id    UUID NOT NULL REFERENCES pay_components(id),
    amount          NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (pay_employee_id, component_id)
);
CREATE INDEX idx_pay_employee_components_emp ON pay_employee_components (tenant_id, pay_employee_id);

CREATE TABLE pay_run_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    pay_run_id      UUID NOT NULL REFERENCES pay_runs(id),
    pay_employee_id UUID NOT NULL REFERENCES pay_employees(id),
    component_id    UUID NOT NULL REFERENCES pay_components(id),
    amount          NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    note            VARCHAR(200),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (pay_run_id, pay_employee_id, component_id)
);
CREATE INDEX idx_pay_run_items_run ON pay_run_items (tenant_id, pay_run_id);

CREATE TABLE pay_payslip_lines (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL,
    payslip_id    UUID NOT NULL REFERENCES pay_payslips(id),
    component_id  UUID REFERENCES pay_components(id),
    code          VARCHAR(30) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    role          VARCHAR(20) NOT NULL,
    sars_code     VARCHAR(8),
    source        VARCHAR(10) NOT NULL CHECK (source IN ('RECURRING','RUN')),
    amount        NUMERIC(15,2) NOT NULL,
    note          VARCHAR(200),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_pay_payslip_lines_slip ON pay_payslip_lines (tenant_id, payslip_id);

-- extra_earnings: taxable extras + bonus + non-taxable earnings on the payslip; extra_deductions: after-tax deductions.
-- Both are 0 for every payslip made before this migration, which is what they had.
ALTER TABLE pay_payslips
    ADD COLUMN extra_earnings   NUMERIC(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN extra_deductions NUMERIC(15,2) NOT NULL DEFAULT 0;
