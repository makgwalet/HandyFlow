-- Security payroll: a shift can be paid once. Audit 1.6: a new overlapping period could re-pay every shift of a PAID period.
-- The service now refuses such shifts; this index makes it impossible even for two approvals running at the same moment.
-- Only REGULAR and OVERTIME lines are limited (a shift has at most one of each); ALLOWANCE and DEDUCTION lines are not tied to one per shift.
--
-- If duplicates already exist this migration stops with their count instead of failing on an unclear index error. Find them first with
-- checks/V840-preflight.sql, decide which period really paid them, and correct the data (do not delete paid history without your payroll owner).
DO $$
DECLARE dup_count integer;
BEGIN
    SELECT count(*) INTO dup_count FROM (
        SELECT tenant_id, shift_id, line_type
        FROM security_payroll_line_items
        WHERE line_type IN ('REGULAR', 'OVERTIME')
        GROUP BY tenant_id, shift_id, line_type
        HAVING count(*) > 1
    ) d;
    IF dup_count > 0 THEN
        RAISE EXCEPTION 'V840: % shift(s) already have more than one payroll line of the same type. Run checks/V840-preflight.sql and resolve them first.', dup_count;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_security_payroll_line_shift_type
    ON security_payroll_line_items (tenant_id, shift_id, line_type)
    WHERE line_type IN ('REGULAR', 'OVERTIME');
