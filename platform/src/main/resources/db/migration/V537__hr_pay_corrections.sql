-- Corrections to a payslip that has already been issued. A correction is an ordinary pay item (back pay for money owed, a recovery for
-- money paid by mistake) that remembers which payslip it corrects. The issued payslip and its run are never changed.

ALTER TABLE hr_pay_adjustments
    ADD COLUMN IF NOT EXISTS corrects_payslip_id UUID REFERENCES hr_payslips(id);
CREATE INDEX IF NOT EXISTS idx_hr_pay_adjustments_payslip ON hr_pay_adjustments (corrects_payslip_id) WHERE corrects_payslip_id IS NOT NULL;
