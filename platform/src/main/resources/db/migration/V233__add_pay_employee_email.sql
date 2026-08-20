-- V233__add_pay_employee_email.sql
--
-- Prerequisite for Payroll Bureau payslip email delivery. Confirmed via
-- real source: CreatePayEmployeeRequest had NO email field at all before
-- this — there was no way to give a PayEmployee an email address through
-- any existing UI or API path. Nullable and optional by design: many
-- employees will never have one on file, and the print/download path
-- (not email) remains the guaranteed-to-work delivery mechanism for them.
ALTER TABLE pay_employees ADD COLUMN email VARCHAR(255);