-- V384 (finance patch F2, 2026-10-14): who posted each journal, and reversals as drafts that a second person posts.
--
-- Before this, a single ACCOUNTING_ADMIN could reverse any posted journal and the reversal was posted at once, with no
-- second person and no record of who did it; and no journal recorded who posted it.
--   posted_by          the person who posted the journal; null when the system posted it (invoice, bill, expense, credit note).
--   reverses_entry_id  set on a reversal: the journal it reverses. The original is marked REVERSED only when this posts.
-- One reversal per journal: a unique index stops a second reversal of the same journal (drafted or posted).
-- Existing rows are left as they are. Re-runnable.

ALTER TABLE acc_journal_entries ADD COLUMN IF NOT EXISTS posted_by UUID;
ALTER TABLE acc_journal_entries ADD COLUMN IF NOT EXISTS reverses_entry_id UUID REFERENCES acc_journal_entries(id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_acc_journal_one_reversal
    ON acc_journal_entries (reverses_entry_id)
    WHERE reverses_entry_id IS NOT NULL AND deleted_at IS NULL;
