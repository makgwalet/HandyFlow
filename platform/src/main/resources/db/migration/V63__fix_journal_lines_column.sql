-- V63__fix_journal_lines_column.sql
--
-- V62 created acc_journal_lines with column name 'entry_id'
-- but the AccJournalLine entity maps to 'journal_entry_id'.
-- This renames the column to match the entity exactly.

ALTER TABLE acc_journal_lines 
    RENAME COLUMN entry_id TO journal_entry_id;
