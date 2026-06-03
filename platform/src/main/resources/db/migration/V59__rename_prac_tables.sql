-- V59__rename_prac_tables.sql
--
-- The accountant module's L3 accounting-core tables were originally named
-- acc_journals, acc_journal_lines, acc_coa_accounts, acc_periods,
-- acc_bank_recons, acc_fixed_assets — clashing with the existing accounting
-- module which maps its entities to the same table names.
--
-- This migration renames those 6 tables to the prac_ prefix to eliminate
-- the collision. Foreign key constraints are automatically updated by Postgres
-- when tables are renamed; no FK recreation is needed.
--
-- Safe to run on a DB where V58 already created these tables.
-- Each ALTER uses IF EXISTS so it's a no-op if the table was never created.

ALTER TABLE IF EXISTS acc_coa_accounts   RENAME TO prac_coa_accounts;
ALTER TABLE IF EXISTS acc_periods        RENAME TO prac_periods;
ALTER TABLE IF EXISTS acc_journals       RENAME TO prac_journals;
ALTER TABLE IF EXISTS acc_journal_lines  RENAME TO prac_journal_lines;
ALTER TABLE IF EXISTS acc_bank_recons    RENAME TO prac_bank_recons;
ALTER TABLE IF EXISTS acc_fixed_assets   RENAME TO prac_fixed_assets;

-- Rename indexes to match new table names (cosmetic but avoids confusion)
ALTER INDEX IF EXISTS idx_acc_journals_client   RENAME TO idx_prac_journals_client;
ALTER INDEX IF EXISTS idx_acc_journal_lines     RENAME TO idx_prac_journal_lines;
ALTER INDEX IF EXISTS idx_acc_journal_account   RENAME TO idx_prac_journal_account;
ALTER INDEX IF EXISTS idx_acc_coa_client        RENAME TO idx_prac_coa_client;
ALTER INDEX IF EXISTS idx_acc_fixed_assets      RENAME TO idx_prac_fixed_assets;
