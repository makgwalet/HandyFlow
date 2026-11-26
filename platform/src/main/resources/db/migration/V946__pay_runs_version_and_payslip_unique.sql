-- V946: Payroll Bureau safety fixes (audit 2026-11-26, D2).
--
-- 1. pay_runs gets an optimistic-lock version, so two people (or two
--    clicks) processing the same run cannot both succeed.
-- 2. One payslip per employee per run, enforced by the database.
--    Existing duplicates, if any, would make the index fail, so this
--    stops with a clear message that says what to clean up first.
ALTER TABLE pay_runs ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

DO $$
DECLARE dup_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO dup_count FROM (
        SELECT pay_run_id, pay_employee_id FROM pay_payslips
        GROUP BY pay_run_id, pay_employee_id HAVING COUNT(*) > 1
    ) d;
    IF dup_count > 0 THEN
        RAISE EXCEPTION 'V946: % pay run/employee pair(s) have more than one payslip in pay_payslips. Remove the duplicate payslips (keep one per employee per run), then run the migration again.', dup_count;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_pay_payslips_run_employee ON pay_payslips (pay_run_id, pay_employee_id);
