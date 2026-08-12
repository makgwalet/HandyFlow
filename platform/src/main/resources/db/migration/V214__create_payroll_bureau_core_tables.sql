-- Payroll core: employees, pay runs, payslips. Second migration for this
-- module — run after V_create_payroll_bureau_tables.sql (practice
-- shell + client portfolio).

CREATE TABLE pay_employees (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID NOT NULL,
    pay_client_id            UUID NOT NULL REFERENCES pay_clients(id),
    employee_number          VARCHAR(30) NOT NULL,
    first_name               VARCHAR(255) NOT NULL,
    last_name                VARCHAR(255) NOT NULL,
    id_number                VARCHAR(20),
    date_of_birth            DATE,
    gross_salary             NUMERIC(15,2) NOT NULL,
    travel_allowance         NUMERIC(15,2) DEFAULT 0,
    pension_contribution     NUMERIC(15,2) DEFAULT 0,
    medical_aid_contribution NUMERIC(15,2) DEFAULT 0,
    bank_name                VARCHAR(100),
    bank_account_number      VARCHAR(50),
    bank_branch_code         VARCHAR(20),
    start_date               DATE,
    end_date                 DATE,
    status                   VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at                TIMESTAMPTZ
);
CREATE INDEX idx_pay_employees_client ON pay_employees (pay_client_id) WHERE deleted_at IS NULL;

CREATE TABLE pay_runs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL,
    pay_client_id    UUID NOT NULL REFERENCES pay_clients(id),
    pay_run_number   VARCHAR(30) NOT NULL,
    period_start     DATE NOT NULL,
    period_end       DATE NOT NULL,
    pay_date         DATE NOT NULL,
    tax_year         INTEGER NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    total_gross      NUMERIC(15,2),
    total_paye       NUMERIC(15,2),
    total_uif        NUMERIC(15,2),
    total_sdl        NUMERIC(15,2),
    total_net        NUMERIC(15,2),
    employee_count   INTEGER,
    processed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_pay_runs_client ON pay_runs (pay_client_id);

CREATE TABLE pay_payslips (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    pay_run_id        UUID NOT NULL REFERENCES pay_runs(id),
    pay_employee_id   UUID NOT NULL REFERENCES pay_employees(id),
    gross_salary      NUMERIC(15,2),
    travel_allowance  NUMERIC(15,2),
    total_earnings    NUMERIC(15,2),
    paye_amount       NUMERIC(15,2),
    uif_employee      NUMERIC(15,2),
    uif_employer      NUMERIC(15,2),
    sdl_amount        NUMERIC(15,2),
    medical_aid       NUMERIC(15,2),
    pension           NUMERIC(15,2),
    total_deductions  NUMERIC(15,2),
    net_pay           NUMERIC(15,2),
    taxable_income    NUMERIC(15,2),
    tax_year          INTEGER,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_pay_payslips_run ON pay_payslips (pay_run_id);
CREATE INDEX idx_pay_payslips_employee ON pay_payslips (pay_employee_id);