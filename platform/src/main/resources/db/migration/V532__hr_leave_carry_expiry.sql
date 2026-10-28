-- Leave: a company can agree that annual leave carried into a new year, if not used within a number of months, is forfeited.
-- Like the carry-over limit, it needs the written agreement named (BCEA section 20(7)) and only applies from a future year.
-- Carried leave cannot be made to expire sooner than the six months employees have after a leave cycle (section 20(2)).

ALTER TABLE hr_leave_policy ADD COLUMN IF NOT EXISTS carry_expiry_months INTEGER;
ALTER TABLE hr_leave_policy DROP CONSTRAINT IF EXISTS hr_leave_policy_carry_expiry_check;
ALTER TABLE hr_leave_policy ADD CONSTRAINT hr_leave_policy_carry_expiry_check
    CHECK (carry_expiry_months IS NULL OR (carry_expiry_months BETWEEN 6 AND 24
        AND effective_year IS NOT NULL AND agreement_ref IS NOT NULL AND length(trim(agreement_ref)) > 0));
