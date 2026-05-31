-- WHY? HR and Payroll module for South African SMEs.
-- BCEA compliant leave management, SARS-compliant PAYE calculation,
-- EMP201 monthly submissions, IRP5 annual certificates.
-- Tax tables seeded for 2025/26 tax year (Mar 2025 – Feb 2026).

-- ── Employees ──────────────────────────────────────────────────────────────
CREATE TABLE hr_employees (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    employee_number     VARCHAR(20) NOT NULL,
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    id_number           VARCHAR(20),
    tax_number          VARCHAR(20),
    date_of_birth       DATE,
    gender              VARCHAR(10) CHECK (gender IN ('MALE','FEMALE','OTHER')),
    race                VARCHAR(20) CHECK (race IN ('AFRICAN','COLOURED','INDIAN','WHITE','OTHER')),
    disability          BOOLEAN NOT NULL DEFAULT false,
    nationality         VARCHAR(50) DEFAULT 'South African',
    email               VARCHAR(200),
    phone               VARCHAR(30),
    address             JSONB,
    employment_type     VARCHAR(20) NOT NULL DEFAULT 'PERMANENT'
        CHECK (employment_type IN ('PERMANENT','FIXED_TERM','PART_TIME','CONTRACTOR')),
    job_title           VARCHAR(100),
    department          VARCHAR(100),
    manager_id          UUID REFERENCES hr_employees(id),
    start_date          DATE NOT NULL,
    end_date            DATE,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE','ON_LEAVE','SUSPENDED','TERMINATED')),
    salary_type         VARCHAR(10) NOT NULL DEFAULT 'MONTHLY'
        CHECK (salary_type IN ('MONTHLY','WEEKLY','HOURLY')),
    gross_salary        NUMERIC(12,2) NOT NULL,
    pay_frequency       VARCHAR(15) NOT NULL DEFAULT 'MONTHLY'
        CHECK (pay_frequency IN ('MONTHLY','WEEKLY','FORTNIGHTLY')),
    bank_name           VARCHAR(100),
    bank_account_number VARCHAR(50),
    bank_branch_code    VARCHAR(10),
    medical_aid_contribution  NUMERIC(10,2) DEFAULT 0,
    pension_contribution      NUMERIC(10,2) DEFAULT 0,
    travel_allowance          NUMERIC(10,2) DEFAULT 0,
    emergency_contact_name    VARCHAR(100),
    emergency_contact_phone   VARCHAR(30),
    emergency_contact_relation VARCHAR(50),
    notes               TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMP,
    version             BIGINT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, employee_number)
);

-- ── Leave balances ─────────────────────────────────────────────────────────
CREATE TABLE hr_leave_balances (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    employee_id     UUID NOT NULL REFERENCES hr_employees(id),
    leave_year      INT NOT NULL,
    leave_type      VARCHAR(30) NOT NULL
        CHECK (leave_type IN ('ANNUAL','SICK','FAMILY_RESPONSIBILITY',
                              'MATERNITY','PATERNITY','UNPAID','STUDY')),
    entitled_days   NUMERIC(5,1) NOT NULL,
    taken_days      NUMERIC(5,1) NOT NULL DEFAULT 0,
    pending_days    NUMERIC(5,1) NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, employee_id, leave_year, leave_type)
);

-- ── Leave requests ─────────────────────────────────────────────────────────
CREATE TABLE hr_leave_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    employee_id     UUID NOT NULL REFERENCES hr_employees(id),
    leave_type      VARCHAR(30) NOT NULL,
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    days_requested  NUMERIC(5,1) NOT NULL,
    reason          TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    approved_by     UUID REFERENCES hr_employees(id),
    approved_at     TIMESTAMP,
    rejection_reason TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ── Disciplinary records ───────────────────────────────────────────────────
CREATE TABLE hr_disciplinary (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    employee_id     UUID NOT NULL REFERENCES hr_employees(id),
    incident_date   DATE NOT NULL,
    incident_type   VARCHAR(30) NOT NULL
        CHECK (incident_type IN ('VERBAL_WARNING','WRITTEN_WARNING',
                                  'FINAL_WARNING','SUSPENSION','DISMISSAL')),
    description     TEXT NOT NULL,
    outcome         TEXT,
    hearing_date    DATE,
    issued_by       UUID REFERENCES hr_employees(id),
    acknowledged    BOOLEAN NOT NULL DEFAULT false,
    acknowledged_at TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ── PAYE tax tables (updated annually per SARS) ────────────────────────────
-- WHY store in DB? Tax tables change every February budget.
-- Storing in DB means we update data, not code.
CREATE TABLE hr_tax_tables (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tax_year        INT NOT NULL,
    income_from     NUMERIC(12,2) NOT NULL,
    income_to       NUMERIC(12,2),
    base_tax        NUMERIC(12,2) NOT NULL,
    marginal_rate   NUMERIC(5,2) NOT NULL,
    UNIQUE (tax_year, income_from)
);

-- ── Tax rebates ────────────────────────────────────────────────────────────
CREATE TABLE hr_tax_rebates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tax_year        INT NOT NULL,
    rebate_type     VARCHAR(20) NOT NULL
        CHECK (rebate_type IN ('PRIMARY','SECONDARY','TERTIARY')),
    amount          NUMERIC(10,2) NOT NULL,
    UNIQUE (tax_year, rebate_type)
);

-- ── Pay runs ───────────────────────────────────────────────────────────────
CREATE TABLE hr_pay_runs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    pay_run_number  VARCHAR(20) NOT NULL,
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,
    pay_date        DATE NOT NULL,
    tax_year        INT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','PROCESSING','COMPLETED','CANCELLED')),
    total_gross     NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_paye      NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_uif       NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_sdl       NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_net       NUMERIC(15,2) NOT NULL DEFAULT 0,
    employee_count  INT NOT NULL DEFAULT 0,
    notes           TEXT,
    processed_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, pay_run_number)
);

-- ── Payslips ───────────────────────────────────────────────────────────────
CREATE TABLE hr_payslips (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    pay_run_id          UUID NOT NULL REFERENCES hr_pay_runs(id),
    employee_id         UUID NOT NULL REFERENCES hr_employees(id),
    gross_salary        NUMERIC(12,2) NOT NULL,
    overtime_amount     NUMERIC(12,2) NOT NULL DEFAULT 0,
    bonus_amount        NUMERIC(12,2) NOT NULL DEFAULT 0,
    travel_allowance    NUMERIC(12,2) NOT NULL DEFAULT 0,
    other_earnings      NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_earnings      NUMERIC(12,2) NOT NULL,
    paye_amount         NUMERIC(12,2) NOT NULL DEFAULT 0,
    uif_employee        NUMERIC(12,2) NOT NULL DEFAULT 0,
    medical_aid         NUMERIC(12,2) NOT NULL DEFAULT 0,
    pension             NUMERIC(12,2) NOT NULL DEFAULT 0,
    other_deductions    NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_deductions    NUMERIC(12,2) NOT NULL,
    uif_employer        NUMERIC(12,2) NOT NULL DEFAULT 0,
    sdl_amount          NUMERIC(12,2) NOT NULL DEFAULT 0,
    net_pay             NUMERIC(12,2) NOT NULL,
    ytd_gross           NUMERIC(12,2) NOT NULL DEFAULT 0,
    ytd_paye            NUMERIC(12,2) NOT NULL DEFAULT 0,
    ytd_uif             NUMERIC(12,2) NOT NULL DEFAULT 0,
    taxable_income      NUMERIC(12,2),
    tax_before_rebate   NUMERIC(12,2),
    primary_rebate      NUMERIC(12,2),
    tax_year            INT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (pay_run_id, employee_id)
);

-- ── EMP201 monthly submissions ─────────────────────────────────────────────
CREATE TABLE hr_emp201 (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    pay_run_id      UUID REFERENCES hr_pay_runs(id),
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,
    due_date        DATE NOT NULL,
    total_paye      NUMERIC(15,2) NOT NULL,
    total_uif       NUMERIC(15,2) NOT NULL,
    total_sdl       NUMERIC(15,2) NOT NULL,
    total_payable   NUMERIC(15,2) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','SUBMITTED','PAID')),
    submitted_at    TIMESTAMP,
    payment_ref     VARCHAR(100),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ── Seed PAYE tax tables 2025/26 ───────────────────────────────────────────
-- Source: SARS Budget 2025 — effective 1 March 2025
INSERT INTO hr_tax_tables
    (tax_year, income_from, income_to, base_tax, marginal_rate)
VALUES
    (2026,        0.00,  237100.00,      0.00, 18.00),
    (2026,  237100.00,  370500.00,  42678.00, 26.00),
    (2026,  370500.00,  512800.00,  77362.00, 31.00),
    (2026,  512800.00,  673000.00, 121475.00, 36.00),
    (2026,  673000.00,  857900.00, 179147.00, 39.00),
    (2026,  857900.00, 1817000.00, 251258.00, 41.00),
    (2026, 1817000.00,       NULL, 644489.00, 45.00);

-- ── Seed tax rebates 2025/26 ───────────────────────────────────────────────
INSERT INTO hr_tax_rebates (tax_year, rebate_type, amount) VALUES
    (2026, 'PRIMARY',   17235.00),
    (2026, 'SECONDARY',  9444.00),
    (2026, 'TERTIARY',   3145.00);

-- ── Indexes ────────────────────────────────────────────────────────────────
CREATE INDEX idx_hr_employees_tenant     ON hr_employees(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_hr_employees_status     ON hr_employees(tenant_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_hr_leave_requests_emp   ON hr_leave_requests(employee_id);
CREATE INDEX idx_hr_leave_requests_dates ON hr_leave_requests(tenant_id, start_date, end_date);
CREATE INDEX idx_hr_leave_balances_emp   ON hr_leave_balances(employee_id);
CREATE INDEX idx_hr_payslips_run         ON hr_payslips(pay_run_id);
CREATE INDEX idx_hr_payslips_employee    ON hr_payslips(employee_id);
CREATE INDEX idx_hr_pay_runs_tenant      ON hr_pay_runs(tenant_id);
CREATE INDEX idx_hr_pay_runs_period      ON hr_pay_runs(tenant_id, period_start);
CREATE INDEX idx_hr_tax_tables_year      ON hr_tax_tables(tax_year);