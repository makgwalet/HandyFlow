-- V303__fix_audit_fk_targets_and_status_checks.sql
--
-- Fixes two classes of schema-vs-code mismatch found while testing the UI. Each made a
-- normal user action fail with HTTP 500 "An unexpected error occurred".
--
-- ── 1. Audit columns whose foreign key points at the WRONG table ────────────────────
-- These columns record WHICH STAFF USER performed an action, and the code writes the
-- logged-in user's id (TenantContext.getCurrentUserId()). But V109/V111/V114/V118 declared
-- them REFERENCES security_guards(id), so PostgreSQL rejects the insert/update: a user id
-- is not a guard id. (Every other audit column in the schema references users(id).)
--
-- Confirmed in a production log for security_guard_screening_records:
--   ERROR: insert or update on table "security_guard_screening_records" violates foreign
--   key constraint "security_guard_screening_records_created_by_fkey"
--   Detail: Key (created_by)=(<user id>) is not present in table "security_guards".
-- and reproduced for all seven columns below on a database built from these migrations.
--
-- This is the SAME defect V52 fixed for HR (hr_leave_requests.approved_by and
-- hr_disciplinary.issued_by), and the fix follows V52: keep the column for audit, drop
-- only the constraint that points at the wrong table. Re-adding it as
--   FOREIGN KEY (col) REFERENCES users(id) NOT VALID
-- is a reasonable follow-up once the flows have been exercised.
--
-- NOT touched, deliberately correct: security_resource_custody.witnessed_by (a second
-- GUARD's id, supplied in the request) and clinic_lab_results.reviewed_by (the controller
-- resolves the practitioner for the user).
--
-- Constraints are located by (table, column, referenced table) instead of by name, so this
-- works whatever the constraint was auto-named, and does nothing if it is already gone.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT t.tbl, t.col, c.conname
        FROM (VALUES
            ('security_shift_swap_requests',    'decided_by',      'security_guards'),
            ('security_device_sessions',        'forced_close_by', 'security_guards'),
            ('security_guard_screening_records','created_by',      'security_guards'),
            ('security_alarm_events',           'triaged_by',      'security_guards'),
            ('security_dispatches',             'dispatched_by',   'security_guards'),
            ('security_principal_vetting',      'created_by',      'security_guards'),
            ('security_declined_principals',    'declined_by',     'security_guards')
        ) AS t(tbl, col, target)
        JOIN pg_constraint c
          ON c.contype = 'f'
         AND c.conrelid  = to_regclass(t.tbl)
         AND c.confrelid = to_regclass(t.target)
         AND array_length(c.conkey, 1) = 1
        JOIN pg_attribute a
          ON a.attrelid = c.conrelid
         AND a.attnum   = c.conkey[1]
         AND a.attname  = t.col
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.tbl, r.conname);
        RAISE NOTICE 'Dropped wrong-target FK % on %.%', r.conname, r.tbl, r.col;
    END LOOP;
END $$;

COMMENT ON COLUMN security_shift_swap_requests.decided_by     IS 'Identity user id of the supervisor who approved/rejected - not a guard id';
COMMENT ON COLUMN security_device_sessions.forced_close_by    IS 'Identity user id of the supervisor who force-closed the session - not a guard id';
COMMENT ON COLUMN security_guard_screening_records.created_by IS 'Identity user id of the staff member who recorded the screening - not a guard id';
COMMENT ON COLUMN security_alarm_events.triaged_by            IS 'Identity user id of the control-room operator who triaged the alarm - not a guard id';
COMMENT ON COLUMN security_dispatches.dispatched_by           IS 'Identity user id of the control-room operator who dispatched - not a guard id';
COMMENT ON COLUMN security_principal_vetting.created_by       IS 'Identity user id of the staff member who ran the vetting check - not a guard id';
COMMENT ON COLUMN security_declined_principals.declined_by    IS 'Identity user id of the staff member who declined the principal - not a guard id';

-- ── 2. Status CHECK constraints that never learned a status the code uses ──────────
-- The Java enum gained a constant, the CHECK list was never widened, so writing that
-- status violates the constraint.

-- ShiftStatus.PULLED is set by Shift.pullFromSite() ("Pull From Site" in the UI), added
-- in V199 with columns only; chk_shift_status (V11) still allowed just 5 statuses.
ALTER TABLE security_shifts DROP CONSTRAINT IF EXISTS chk_shift_status;
ALTER TABLE security_shifts ADD CONSTRAINT chk_shift_status
    CHECK (status IN ('SCHEDULED','ACTIVE','COMPLETED','MISSED','CANCELLED','PULLED'));

-- InvoiceStatus.OVERPAID is set by Invoice when a payment exceeds the total (and
-- InvoiceRepository already queries for it); chk_invoices_status (V8) never allowed it,
-- so recording an overpayment failed.
ALTER TABLE invoices DROP CONSTRAINT IF EXISTS chk_invoices_status;
ALTER TABLE invoices ADD CONSTRAINT chk_invoices_status
    CHECK (status IN ('DRAFT','ISSUED','PARTIALLY_PAID','PAID','OVERPAID','OVERDUE','CANCELLED'));
