-- V949: room for encrypted values (audit 2026-11-26, D3).
-- ID number, tax number and bank account number are now stored encrypted
-- (prefix "enc1:" + base64 AES-GCM), which is longer than the plain value.
-- Existing rows stay readable as plain text until the one-off backfill
-- (app.payrollbureau.encrypt-existing=true) converts them.
ALTER TABLE pay_employees ALTER COLUMN id_number TYPE TEXT;
ALTER TABLE pay_employees ALTER COLUMN tax_number TYPE TEXT;
ALTER TABLE pay_employees ALTER COLUMN bank_account_number TYPE TEXT;
ALTER TABLE pay_employee_change_requests ALTER COLUMN prev_bank_account TYPE TEXT;
ALTER TABLE pay_employee_change_requests ALTER COLUMN new_bank_account TYPE TEXT;
