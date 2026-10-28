-- Payroll: which kind of bank account an employee is paid into (BankServ files carry an account type), kept on the employee and copied onto
-- each payment when the payments are made up. Everything already on file is a current (cheque) account, which is what the file wrote before.

ALTER TABLE hr_employees
    ADD COLUMN IF NOT EXISTS bank_account_type VARCHAR(12) NOT NULL DEFAULT 'CURRENT'
        CHECK (bank_account_type IN ('CURRENT','SAVINGS','TRANSMISSION','BOND'));

ALTER TABLE hr_pay_run_payments
    ADD COLUMN IF NOT EXISTS account_type VARCHAR(12) NOT NULL DEFAULT 'CURRENT'
        CHECK (account_type IN ('CURRENT','SAVINGS','TRANSMISSION','BOND'));
