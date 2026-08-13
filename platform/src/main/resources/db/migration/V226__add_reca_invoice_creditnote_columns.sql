-- Adds the credit-note flag columns that RecAgencyInvoice.java already
-- expects (creditNoteRequired/creditNoteReason, added in Section 83's
-- guarantee-period workflow) but were never actually migrated into the
-- database — the entity change landed, the schema change didn't.
--
-- Deliberately a NEW migration, not an edit to
-- V223__create_recruitment_agency_billing_tables.sql — that file may
-- already be applied (Flyway checksums applied migrations; editing one
-- retroactively would break validation, not fix anything).

ALTER TABLE reca_invoices ADD COLUMN credit_note_required BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE reca_invoices ADD COLUMN credit_note_reason TEXT;