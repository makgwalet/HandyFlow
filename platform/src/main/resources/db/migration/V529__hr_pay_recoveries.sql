-- Payroll: recovering an overpayment. It is entered like a bonus or back pay but is taken off net pay, after tax, by the next pay run
-- whose period ends on or after pay_from (whole, or left open if net pay cannot bear it).
DO $$
DECLARE c TEXT;
BEGIN
    FOR c IN SELECT con.conname FROM pg_constraint con
             WHERE con.conrelid = 'hr_pay_adjustments'::regclass AND con.contype = 'c' AND pg_get_constraintdef(con.oid) LIKE '%kind%'
    LOOP
        EXECUTE format('ALTER TABLE hr_pay_adjustments DROP CONSTRAINT %I', c);
    END LOOP;
END $$;
ALTER TABLE hr_pay_adjustments ADD CONSTRAINT hr_pay_adjustments_kind_check CHECK (kind IN ('BONUS','BACK_PAY','RECOVERY'));
