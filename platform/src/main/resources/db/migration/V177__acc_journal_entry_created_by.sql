-- V__PLACEHOLDER_acc_journal_entry_created_by.sql
-- RENAME: replace __PLACEHOLDER__ with your real next Flyway version.
--
-- Nullable, deliberately: every existing journal entry predates this
-- column and will have created_by = NULL. The maker-checker check being
-- added alongside this treats NULL created_by as "no creator recorded —
-- skip the check" rather than blocking, so old entries stay postable
-- (this column only matters going forward, for entries created through
-- the manual "New Journal Entry" flow from now on).
--
-- No CHECK constraint here — just a plain nullable UUID. No new status
-- string is being introduced anywhere in this feature, unlike AP's
-- maker-checker (SECOND_APPROVAL), so there's nothing here that risks
-- the VARCHAR-length or CHECK-constraint problems that hit twice there.

ALTER TABLE acc_journal_entries
    ADD COLUMN created_by UUID;