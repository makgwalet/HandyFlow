-- Retrenchment severance: a lump sum that needs a SARS tax directive. It is kept outside the monthly PAYE: the tax SARS directs is
-- recorded and taken from the payment, and the payslip, the EMP201 and the tax certificate (codes 3901 and 4115) show it separately.

ALTER TABLE hr_pay_adjustments DROP CONSTRAINT IF EXISTS hr_pay_adjustments_kind_check;
ALTER TABLE hr_pay_adjustments ADD CONSTRAINT hr_pay_adjustments_kind_check CHECK (kind IN ('BONUS','BACK_PAY','RECOVERY','NOTICE_PAY','SEVERANCE'));

ALTER TABLE hr_pay_adjustments
    ADD COLUMN IF NOT EXISTS directive_number     VARCHAR(40),
    ADD COLUMN IF NOT EXISTS directive_tax        NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS refused_alternative  BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE hr_payslips
    ADD COLUMN IF NOT EXISTS severance_amount     NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS severance_tax        NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS severance_directive  VARCHAR(40);

-- A retrenchment is a way employment ends.
ALTER TABLE hr_employees DROP CONSTRAINT IF EXISTS hr_employees_end_type_check;
ALTER TABLE hr_employees ADD CONSTRAINT hr_employees_end_type_check
    CHECK (end_type IS NULL OR end_type IN ('RESIGNATION','EMPLOYER_NOTICE','SUMMARY_DISMISSAL','RETRENCHMENT','CONTRACT_END','MUTUAL','OTHER'));
