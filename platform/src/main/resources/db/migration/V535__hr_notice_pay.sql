-- Pay in place of notice: a new kind of pay item, and its own line on the payslip. It is taxed as an annual payment, like a bonus.

ALTER TABLE hr_pay_adjustments DROP CONSTRAINT IF EXISTS hr_pay_adjustments_kind_check;
ALTER TABLE hr_pay_adjustments ADD CONSTRAINT hr_pay_adjustments_kind_check CHECK (kind IN ('BONUS','BACK_PAY','RECOVERY','NOTICE_PAY'));

ALTER TABLE hr_payslips
    ADD COLUMN IF NOT EXISTS notice_pay_amount NUMERIC(12,2) NOT NULL DEFAULT 0;
