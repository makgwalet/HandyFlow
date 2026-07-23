-- V__PLACEHOLDER_acc_bank_account_low_balance_threshold.sql
-- RENAME: replace __PLACEHOLDER__ with your real next Flyway version.
--
-- Nullable, deliberately: a bank account with no threshold set gets no
-- low-balance alerting at all, rather than defaulting to some guessed
-- number that would be wrong for most accounts (a savings buffer sitting
-- "low" on purpose vs a working cheque account genuinely running dry
-- mean completely different things, per-account, not globally).
--
-- Plain NUMERIC, no CHECK constraint — nothing here risks the
-- VARCHAR-length or CHECK-constraint problems that hit twice already in
-- this exact module (entry_type, ap_bills status).

ALTER TABLE acc_bank_accounts
    ADD COLUMN low_balance_threshold NUMERIC(15,2);