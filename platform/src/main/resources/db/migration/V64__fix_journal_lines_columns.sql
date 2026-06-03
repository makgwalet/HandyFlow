-- V64__fix_journal_lines_columns.sql
--
-- AccJournalLine entity expects:
--   sort_order   (table has: line_order)
-- Rename to match. While here, also pre-emptively rename
-- debit_amount/credit_amount if the entity uses debit/credit directly.
-- (We'll fix debit/credit in V65 if needed — one step at a time.)

ALTER TABLE acc_journal_lines 
    RENAME COLUMN line_order TO sort_order;
