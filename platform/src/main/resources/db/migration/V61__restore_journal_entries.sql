-- V61__restore_journal_entries.sql
--
-- V60 incorrectly renamed acc_journal_entries → acc_journal_lines.
-- The accounting module entity maps to acc_journal_entries (correct original name).
-- The accountant module entity maps to prac_journal_lines (separate table, correct).
-- Nothing in the codebase maps to acc_journal_lines.
--
-- This migration simply renames acc_journal_lines back to acc_journal_entries,
-- restoring the state that existed before V60 ran.

ALTER TABLE IF EXISTS acc_journal_lines RENAME TO acc_journal_entries;
