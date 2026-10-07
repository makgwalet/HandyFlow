-- V333__security_hr_link.sql
--
-- Links a guard to an HR employee record, and a complaint to the HR disciplinary case it was referred to.
-- The link is optional and one-to-one: a guard is linked to at most one employee, and an employee to at most one
-- guard. HR keeps ownership of the employee and the disciplinary case; Security stores only the identifiers.

ALTER TABLE security_guards ADD COLUMN employee_id UUID;

CREATE UNIQUE INDEX uq_security_guard_employee
    ON security_guards (tenant_id, employee_id)
    WHERE employee_id IS NOT NULL AND deleted_at IS NULL;

ALTER TABLE security_guard_complaints
    ADD COLUMN hr_disciplinary_id UUID,
    ADD COLUMN hr_referred_at     TIMESTAMPTZ,
    ADD COLUMN hr_referred_by     VARCHAR(200);

COMMENT ON COLUMN security_guards.employee_id IS 'HR employee this guard is the same person as (hr_employees.id). Optional.';
COMMENT ON COLUMN security_guard_complaints.hr_disciplinary_id IS 'The hr_disciplinary case created when this complaint was referred to HR. Set once.';
