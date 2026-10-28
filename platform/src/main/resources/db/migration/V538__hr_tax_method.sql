-- Tax method. PERIODIC (the default) taxes each month's regular pay on its own, annualised. AVERAGING taxes the year to date: the pay so far is
-- annualised, the year's tax is worked out on that and spread over the months gone, and what was already paid is taken off.
ALTER TABLE hr_payroll_settings ADD COLUMN IF NOT EXISTS tax_method VARCHAR(12) NOT NULL DEFAULT 'PERIODIC';
ALTER TABLE hr_payroll_settings DROP CONSTRAINT IF EXISTS hr_payroll_settings_tax_method_chk;
ALTER TABLE hr_payroll_settings ADD CONSTRAINT hr_payroll_settings_tax_method_chk CHECK (tax_method IN ('PERIODIC', 'AVERAGING'));

-- What each payslip contributes to the year-to-date: the month's regular taxable pay (no bonus, back pay or notice pay) and the PAYE on it.
-- Older payslips are filled from what they hold: their annual taxable income / 12 and all their PAYE. That is slightly high if they had a bonus,
-- which is why the method is best changed at the start of a tax year.
ALTER TABLE hr_payslips ADD COLUMN IF NOT EXISTS regular_taxable NUMERIC(14,2);
ALTER TABLE hr_payslips ADD COLUMN IF NOT EXISTS paye_regular NUMERIC(12,2);
UPDATE hr_payslips SET regular_taxable = ROUND(COALESCE(taxable_income, 0) / 12, 2), paye_regular = COALESCE(paye_amount, 0)
 WHERE regular_taxable IS NULL;
