-- V234__add_pay_employee_tax_phone.sql
--
-- PayEmployee genuinely had no taxNumber/phone columns at all (confirmed
-- against real source — HrEmployee has both, PayEmployee had neither).
-- Nullable, same reasoning as V233's email column: not every employee
-- record will have these filled in immediately, especially existing ones.
ALTER TABLE pay_employees ADD COLUMN tax_number VARCHAR(50);
ALTER TABLE pay_employees ADD COLUMN phone VARCHAR(50);