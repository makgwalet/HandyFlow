-- Bank payment file: the company's own account (the one the salaries are paid from, needed by the BankServ layout), an optional
-- second-person check before payments are confirmed, and who downloaded each payment's file.

ALTER TABLE hr_payroll_settings
    ADD COLUMN IF NOT EXISTS bank_nominated_branch  VARCHAR(6),
    ADD COLUMN IF NOT EXISTS bank_nominated_account VARCHAR(11),
    ADD COLUMN IF NOT EXISTS bank_file_second_check BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE hr_pay_run_payments
    ADD COLUMN IF NOT EXISTS exported_by UUID;
