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

-- IDEMPOTENT: uses IF NOT EXISTS specifically because this migration hit a
-- real "column already exists" error in practice — these ALTER TABLE
-- statements had already run successfully once (under some Flyway
-- version), and a second attempt under a new version number collided with
-- them. Making this safe to run either way, rather than relying on
-- getting Flyway's version bookkeeping exactly right, given placeholder
-- migrations in this session get manually renamed and occasionally
-- duplicated in the process.
ALTER TABLE ap_bills
    ADD COLUMN IF NOT EXISTS first_approved_by UUID,
    ADD COLUMN IF NOT EXISTS first_approved_at TIMESTAMP;

-- Widen the status check constraint to allow the new intermediate status,
-- if one exists. If your ap_bills.status column has no CHECK constraint
-- (application-level validation only, as the entity code suggests), this
-- ALTER is a no-op safety net — harmless either way.
-- REVISION: the intermediate status is "SECOND_APPROVAL", not
-- "AWAITING_SECOND_APPROVAL" as originally written here — confirmed via a
-- real DataIntegrityViolationException ("value too long for type
-- character varying(20)") that ap_bills.status is VARCHAR(20), which the
-- original 24-character value didn't fit. Never verified against the real
-- CREATE TABLE before picking that name; this is that mistake's fix.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'ap_bills' AND constraint_type = 'CHECK'
        AND constraint_name LIKE '%status%'
    ) THEN
        RAISE NOTICE 'ap_bills has a status CHECK constraint — verify it allows SECOND_APPROVAL manually, this migration does not attempt to alter an unknown constraint definition.';
    END IF;
END $$;