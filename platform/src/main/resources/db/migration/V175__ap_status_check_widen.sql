-- V__PLACEHOLDER_ap_status_check_widen.sql
-- RENAME: replace __PLACEHOLDER__ with your real next Flyway version —
-- a NEW one, after whatever V174 ended up being. Do NOT touch V174's
-- content again: it already ran successfully (confirmed by the checksum
-- mismatch error), and Flyway checksums migrations specifically to
-- prevent silently editing history. Once a migration has succeeded
-- anywhere, any further change needs a new version, never an edit to the
-- old file.
--
-- CONFIRMED, not guessed: a real DataIntegrityViolationException against
-- this exact database surfaced the constraint's real name
-- (ap_bills_status_check) directly. The existing allowed values below are
-- reconstructed from ApBill.java's own domain code — DRAFT (create()),
-- APPROVED (approve()), PAID (markPaid()), OVERDUE (markOverdue()),
-- CANCELLED (cancel()) are the only five values the application ever
-- assigns, so the constraint almost certainly matches exactly that set.
-- DROP + ADD (not a plain ADD) so this is safe to re-run regardless of
-- whether a prior attempt partially succeeded.

ALTER TABLE ap_bills DROP CONSTRAINT IF EXISTS ap_bills_status_check;
ALTER TABLE ap_bills ADD CONSTRAINT ap_bills_status_check
    CHECK (status IN ('DRAFT','APPROVED','PAID','OVERDUE','CANCELLED','SECOND_APPROVAL'));

DO $$
BEGIN
    RAISE NOTICE 'ap_bills_status_check widened to include SECOND_APPROVAL. If this table has OTHER status values beyond DRAFT/APPROVED/PAID/OVERDUE/CANCELLED that were not visible from ApBill.java''s domain code, this constraint will now be too narrow — verify against pg_get_constraintdef if in doubt.';
END $$;