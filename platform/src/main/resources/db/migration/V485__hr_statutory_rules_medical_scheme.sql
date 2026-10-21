-- HR slice 1: payroll correctness.
--
-- 1. Versioned statutory parameters per tax year, with a source note and an approval state.
--    A tax year is named by the year it ENDS, the way SARS names it: "2027 tax year" = 1 March 2026 to 28 February 2027.
--    (The V22 seed already used this naming: 2026 = 1 March 2025 to 28 February 2026.)
-- 2. The 2027 brackets and rebates (hr_tax_tables / hr_tax_rebates are national, so they have no tenant column).
-- 3. Medical scheme membership with effective dates, so the medical scheme fees tax credit comes from the number of
--    people covered in each period and old periods can be recalculated without losing earlier values.
--
-- Sources, all read on 2026-10-21:
--   Rates of tax for individuals     https://www.sars.gov.za/tax-rates/income-tax/rates-of-tax-for-individuals/
--   Medical tax credit rates         https://www.sars.gov.za/tax-rates/medical-tax-credit-rates/
--   UIF ceiling                      https://www.sars.gov.za/types-of-tax/unemployment-insurance-fund/
--   Employer guide 2026 and 2027     https://www.sars.gov.za/guide-for-employers-in-respect-of-employees-tax-2027/
--   Monthly deduction tables         https://www.sars.gov.za/latest-news/2026-employees-tax-deduction-tables/

CREATE TABLE hr_statutory_tax_years (
    tax_year               INT PRIMARY KEY,
    label                  VARCHAR(20)   NOT NULL,
    effective_from         DATE          NOT NULL,
    effective_to           DATE          NOT NULL,
    uif_monthly_ceiling    NUMERIC(12,2) NOT NULL,
    uif_rate_employee      NUMERIC(6,4)  NOT NULL,
    uif_rate_employer      NUMERIC(6,4)  NOT NULL,
    sdl_rate               NUMERIC(6,4)  NOT NULL,
    sdl_threshold          NUMERIC(14,2) NOT NULL,
    retirement_cap_amount  NUMERIC(14,2) NOT NULL,
    retirement_cap_pct     NUMERIC(6,4)  NOT NULL,
    travel_taxable_pct     NUMERIC(6,4)  NOT NULL,
    mtc_taxpayer           NUMERIC(10,2) NOT NULL,
    mtc_first_dependant    NUMERIC(10,2) NOT NULL,
    mtc_additional         NUMERIC(10,2) NOT NULL,
    status                 VARCHAR(12)   NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'APPROVED')),
    source_note            TEXT          NOT NULL,
    approved_by            UUID,
    approved_at            TIMESTAMP,
    approval_note          VARCHAR(500),
    created_at             TIMESTAMP     NOT NULL DEFAULT NOW(),
    CHECK (effective_to > effective_from),
    CHECK (status = 'DRAFT' OR approved_at IS NOT NULL)
);

-- 2025/26 (SARS "2026"). The SARS rates page says the 2026 brackets are unchanged from 2025 and 2024, which matches the V22 seed.
INSERT INTO hr_statutory_tax_years
    (tax_year, label, effective_from, effective_to, uif_monthly_ceiling, uif_rate_employee, uif_rate_employer,
     sdl_rate, sdl_threshold, retirement_cap_amount, retirement_cap_pct, travel_taxable_pct,
     mtc_taxpayer, mtc_first_dependant, mtc_additional, status, source_note, approved_at, approval_note)
VALUES
    (2026, '2025/26', DATE '2025-03-01', DATE '2026-02-28', 17712.00, 0.0100, 0.0100,
     0.0100, 500000.00, 350000.00, 0.2750, 0.8000,
     364.00, 364.00, 246.00, 'APPROVED',
     'SARS rates of tax for individuals; SARS medical tax credit rates (2026 column: R364 / R364 / R246); SARS employer guide for employees tax 2026 (retirement cap R350 000, primary rebate R17 235); SARS UIF page (ceiling R17 712 from 1 June 2021); SARS monthly deduction tables 2026. Read 2026-10-21.',
     NOW(),
     'Loaded by migration V485 and checked against the SARS pages in the source note on 2026-10-21. Owner sign-off still to be recorded.'),
-- 2026/27 (SARS "2027")
    (2027, '2026/27', DATE '2026-03-01', DATE '2027-02-28', 17712.00, 0.0100, 0.0100,
     0.0100, 500000.00, 430000.00, 0.2750, 0.8000,
     376.00, 376.00, 254.00, 'APPROVED',
     'SARS rates of tax for individuals (2027 table, updated 25 Feb 2026); SARS medical tax credit rates (2027 column: R376 / R752 with one dependant / R254 each additional); SARS employer guide for employees tax 2027 (retirement cap R430 000); SARS UIF page (ceiling R17 712, no change shown); SARS monthly deduction tables 2027 (effective 2026-03-01). Read 2026-10-21.',
     NOW(),
     'Loaded by migration V485 and checked against the SARS pages in the source note on 2026-10-21. Owner sign-off still to be recorded.');

-- 2026/27 brackets and rebates
INSERT INTO hr_tax_tables (tax_year, income_from, income_to, base_tax, marginal_rate) VALUES
    (2027,       0.00,  245100.00,      0.00, 18.00),
    (2027,  245100.00,  383100.00,  44118.00, 26.00),
    (2027,  383100.00,  530200.00,  79998.00, 31.00),
    (2027,  530200.00,  695800.00, 125599.00, 36.00),
    (2027,  695800.00,  887000.00, 185215.00, 39.00),
    (2027,  887000.00, 1878600.00, 259783.00, 41.00),
    (2027, 1878600.00,       NULL, 666339.00, 45.00);

INSERT INTO hr_tax_rebates (tax_year, rebate_type, amount) VALUES
    (2027, 'PRIMARY',   17820.00),
    (2027, 'SECONDARY',  9765.00),
    (2027, 'TERTIARY',   3249.00);

-- Medical scheme membership. dependants = people covered besides the employee. The credit is worked out from
-- 1 + dependants. Rows are never edited to change history: close the old one and add a new one.
CREATE TABLE hr_medical_scheme_memberships (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    employee_id     UUID         NOT NULL REFERENCES hr_employees(id),
    scheme_name     VARCHAR(120) NOT NULL,
    member_number   VARCHAR(60),
    dependants      INT          NOT NULL DEFAULT 0 CHECK (dependants BETWEEN 0 AND 20),
    effective_from  DATE         NOT NULL,
    effective_to    DATE,
    notes           VARCHAR(500),
    created_by      UUID,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX idx_hr_medical_memberships_employee ON hr_medical_scheme_memberships (tenant_id, employee_id, effective_from);
