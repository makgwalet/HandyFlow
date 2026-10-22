-- HR payroll: pay for the days actually employed and paid.
--
-- Until now a pay run paid every active employee a full month: someone who joined mid-month was paid the whole month, someone who left
-- mid-month and was marked terminated before the run dropped out of it altogether (their last pay was never calculated), and approved
-- unpaid leave was not deducted. The calculation now works out, per employee:
--   * the working days in the pay period (Monday to Friday, public holidays left out),
--   * the working days the person was employed (part months for joiners and leavers),
--   * the working days of approved unpaid leave, which are not paid,
--   * on the last pay, the annual leave still owing, paid out.
-- The payslip records those figures so they can be shown and checked. Nothing here changes a payslip that was already made: the new
-- columns are empty (days) or zero (amounts) on those, and "days_in_period" being empty means "paid in full".

ALTER TABLE hr_payslips
    ADD COLUMN days_in_period       SMALLINT,
    ADD COLUMN days_paid            NUMERIC(6,2),
    ADD COLUMN proration_amount     NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN unpaid_leave_days    NUMERIC(6,2)  NOT NULL DEFAULT 0,
    ADD COLUMN unpaid_leave_amount  NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN leave_payout_days    NUMERIC(6,2)  NOT NULL DEFAULT 0,
    ADD COLUMN leave_payout_amount  NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN final_pay            BOOLEAN       NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN hr_payslips.proration_amount    IS 'Salary not paid because the employee joined or left part way through the period (already taken off gross_salary)';
COMMENT ON COLUMN hr_payslips.unpaid_leave_amount IS 'Salary not paid for approved unpaid leave (already taken off gross_salary)';
COMMENT ON COLUMN hr_payslips.leave_payout_amount IS 'Annual leave paid out on the last pay (included in total_earnings, reported with the basic salary on the tax certificate)';

-- Finding a leaver who has not had a last pay yet looks for payslips by employee in runs that end on or after the leaving date.
CREATE INDEX IF NOT EXISTS hr_payslips_employee_run ON hr_payslips (employee_id, pay_run_id);
