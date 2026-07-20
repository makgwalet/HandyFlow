-- ═══════════════════════════════════════════════════════════════════════════
-- IMPORTANT: rename this file before running it — "V999" is a placeholder.
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Adds FICA document expiry reminder tracking to acc_fica_documents —
-- closes the accountant module audit's "FICA/TCS PIN expiry reminders"
-- gap for the FICA half. TCS PIN reminders were already built earlier;
-- this is the same pattern applied to acc_fica_documents.expiry_date,
-- which was found (alongside acc_periods/acc_coa_accounts) sitting
-- unused after this module's real V58 schema was actually read in full.
--
-- Same belt-and-braces reasoning as acc_clients' own TCS PIN reminder
-- columns: a flag plus an exact-date match together protect against
-- both same-day duplicate sends AND the scheduler missing a day
-- entirely (server down) and never getting a second chance once the
-- date has passed. See that earlier migration's own comment for the
-- full reasoning — not repeated here.
--
-- No reset-on-update logic needed here (unlike TCS PIN, where
-- updateTcsPin() can set a new expiry on an EXISTING client record) —
-- FICA documents have no edit flow at all, only upload/verify/delete.
-- Changing an expiry means uploading a new document (a new row, which
-- naturally starts with fresh false flags) and deleting the old one.

ALTER TABLE acc_fica_documents
    ADD COLUMN IF NOT EXISTS reminder_30_sent BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS reminder_7_sent  BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS reminder_1_sent  BOOLEAN NOT NULL DEFAULT false;