-- V52__fix_leave_approved_by_fk.sql
--
-- WHY: hr_leave_requests.approved_by was created with a FK to hr_employees.id
-- but the value stored there is a USER/IDENTITY id (from the JWT principal),
-- not an employee id. These live in a different table (e.g. users / tenant_users).
-- The FK fires every time a manager approves or rejects leave.
--
-- FIX: Drop the erroneous FK constraint. The column is kept for audit purposes
-- (who approved). We just remove the referential integrity check that points
-- at the wrong table. The application code continues to store the approver id;
-- it simply will no longer be validated against hr_employees.

ALTER TABLE hr_leave_requests
    DROP CONSTRAINT IF EXISTS hr_leave_requests_approved_by_fkey;

-- Also check for the disciplinary issuedBy constraint which will have the same problem
ALTER TABLE hr_disciplinary
    DROP CONSTRAINT IF EXISTS hr_disciplinary_issued_by_fkey;

COMMENT ON COLUMN hr_leave_requests.approved_by
    IS 'Identity user id of the manager who approved/rejected — not an employee id';

COMMENT ON COLUMN hr_disciplinary.issued_by
    IS 'Identity user id of the manager who issued the record — not an employee id';
