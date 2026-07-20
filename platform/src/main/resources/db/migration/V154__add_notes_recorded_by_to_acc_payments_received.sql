-- ═══════════════════════════════════════════════════════════════════════════
-- IMPORTANT: rename this file before running it — "V999" is a placeholder.
--
-- REPLACES the earlier V153__create_acc_payments_received.sql — DELETE
-- that file entirely before running this one. It failed on startup with
-- "relation acc_payments_received already exists" because the table was
-- already created in V58__accountant_module.sql, before the payment-
-- recording feature in this codebase was ever built. It just sat unused
-- — AccountantService.toFeeNoteResponse() hardcoded amountPaid to ZERO
-- instead of ever querying it.
-- ═══════════════════════════════════════════════════════════════════════════
--
-- V58's version of this table is leaner than AccPaymentReceived.java
-- originally assumed (confirmed by reading the real V58 migration after
-- the startup failure above):
--   - payment_method: nullable, not required
--   - reference: VARCHAR(100), not 255
--   - notes, recorded_by, recorded_by_name: did not exist at all
--
-- payment_method/reference needed no schema change — only the entity's
-- mapping was wrong, and that's fixed directly in AccPaymentReceived.java
-- (nullable removed, reference length corrected to 100), not here.
--
-- notes/recorded_by/recorded_by_name are being kept in the feature —
-- they're genuinely useful for a real payment audit trail (context on
-- the payment, and who recorded it), not scope creep — so this adds
-- them to the existing table rather than stripping the feature down to
-- match V58's original, narrower design.
--
-- Not adding a NOT NULL constraint to payment_method: this table's row
-- history before this feature existed isn't known, and asserting NOT
-- NULL retroactively without that knowledge is a real risk, not a
-- formality. Enforcement for payments created going forward is at the
-- application layer instead (RecordPaymentRequest.paymentMethod() is
-- @NotBlank).

ALTER TABLE acc_payments_received
    ADD COLUMN IF NOT EXISTS notes             TEXT,
    ADD COLUMN IF NOT EXISTS recorded_by       UUID,
    ADD COLUMN IF NOT EXISTS recorded_by_name  VARCHAR(255);