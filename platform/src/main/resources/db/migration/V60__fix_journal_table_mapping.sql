-- V60__fix_journal_table_mapping.sql
--
-- The accounting module's AccJournalLine entity has @Table(name = "acc_journal_lines")
-- but the actual table in the DB is acc_journal_entries (created by an earlier migration).
-- This migration creates acc_journal_lines as an alias view OR simply creates the table
-- if it truly never existed.
--
-- ACTUAL FIX: Create acc_journal_lines pointing to the same structure as acc_journal_entries
-- so the accounting module entity validation passes.
--
-- Looking at the DB: acc_journal_entries exists, acc_journal_lines does not.
-- The accounting module's AccJournalLine is mapped to acc_journal_lines (wrong).
-- Solution: rename acc_journal_entries to acc_journal_lines to match the entity.

ALTER TABLE IF EXISTS acc_journal_entries RENAME TO acc_journal_lines;

-- prac_journals and prac_journal_lines already exist from V58/V59 — leave them as-is.
-- The accountant module's AccJournal maps to prac_journals ✓
-- The accountant module's AccJournalLine maps to prac_journal_lines ✓
-- The accounting module's AccJournalLine maps to acc_journal_lines ✓ (after rename above)
