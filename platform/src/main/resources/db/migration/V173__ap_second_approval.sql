-- V__PLACEHOLDER_ap_second_approval.sql
-- RENAME: replace __PLACEHOLDER__ with your real next Flyway version.
-- Independent of all prior AP migrations this session.
--
-- WHY separate columns, not reusing existing fields? journal_entry_id only
-- gets set on FINAL approval (when the journal actually posts) — a bill
-- awaiting its second approval has no journal yet, deliberately, since
-- posting before both approvals are in would defeat the whole point of a
-- second approver existing. first_approved_by/at track who gave the FIRST
-- approval so the second approver can be checked against it (must be a
-- different person — a maker-checker control is meaningless if the same
-- person can satisfy both steps).

ALTER TABLE ap_bills
    ADD COLUMN first_approved_by UUID,
    ADD COLUMN first_approved_at TIMESTAMP;

-- Widen the status check constraint to allow the new intermediate status,
-- if one exists. If your ap_bills.status column has no CHECK constraint
-- (application-level validation only, as the entity code suggests), this
-- ALTER is a no-op safety net — harmless either way.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'ap_bills' AND constraint_type = 'CHECK'
        AND constraint_name LIKE '%status%'
    ) THEN
        RAISE NOTICE 'ap_bills has a status CHECK constraint — verify it allows AWAITING_SECOND_APPROVAL manually, this migration does not attempt to alter an unknown constraint definition.';
    END IF;
END $$;