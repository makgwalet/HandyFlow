-- Employment tax incentive (ETI). Off until the business says it qualifies. The amounts live in a table with the date they apply from,
-- so a change in the law is a new row, not a code change. The row seeded here is the one SARS published for 1 April 2025; the business's
-- payroll specialist should confirm it is still the current one.
CREATE TABLE IF NOT EXISTS hr_eti_rules (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    effective_from     DATE          NOT NULL,
    effective_to       DATE,
    full_month_min     NUMERIC(10,2) NOT NULL,   -- pay for 160 hours must be at least this
    remuneration_cap   NUMERIC(10,2) NOT NULL,   -- and below this
    max_first          NUMERIC(10,2) NOT NULL,   -- most per month in the first 12 qualifying months
    max_second         NUMERIC(10,2) NOT NULL,   -- and in the second 12
    taper_start        NUMERIC(10,2) NOT NULL,   -- above this the amount tapers off
    taper_rate_first   NUMERIC(6,4)  NOT NULL,
    taper_rate_second  NUMERIC(6,4)  NOT NULL,
    note               VARCHAR(300)
);
INSERT INTO hr_eti_rules (effective_from, full_month_min, remuneration_cap, max_first, max_second,
                          taper_start, taper_rate_first, taper_rate_second, note)
SELECT DATE '2025-04-01', 2500, 7500, 1500, 750, 5500, 0.75, 0.375,
       'SARS: amounts from 1 April 2025. Confirm against the current SARS guide before relying on it.'
WHERE NOT EXISTS (SELECT 1 FROM hr_eti_rules WHERE effective_from = DATE '2025-04-01');

ALTER TABLE hr_payroll_settings ADD COLUMN IF NOT EXISTS eti_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Months of ETI already claimed for the person (by this employer before payroll was kept here, or by another employer), and a reason they
-- cannot qualify (a domestic worker, a connected person ...). No reason means the person may qualify.
ALTER TABLE hr_employees ADD COLUMN IF NOT EXISTS eti_prior_months INT NOT NULL DEFAULT 0;
ALTER TABLE hr_employees ADD COLUMN IF NOT EXISTS eti_excluded_reason VARCHAR(200);

-- What each payslip claimed and which qualifying month it was (1 to 24). The EMP201 carries the totals.
ALTER TABLE hr_payslips ADD COLUMN IF NOT EXISTS eti_amount NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE hr_payslips ADD COLUMN IF NOT EXISTS eti_month INT;
ALTER TABLE hr_emp201 ADD COLUMN IF NOT EXISTS total_eti NUMERIC(12,2) NOT NULL DEFAULT 0;      -- ETI used against the PAYE of this return
ALTER TABLE hr_emp201 ADD COLUMN IF NOT EXISTS eti_carried NUMERIC(12,2) NOT NULL DEFAULT 0;   -- ETI worked out above the PAYE, carried forward
