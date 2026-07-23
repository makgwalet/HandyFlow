-- V__PLACEHOLDER_acc_journal_sequences_backfill.sql
-- RENAME: replace __PLACEHOLDER__ with your real next Flyway version.
--
-- WHY THIS EXISTS: JournalNumberGenerator.next() always starts a new
-- tenant+year sequence at 1 (via ON CONFLICT DO NOTHING on first use).
-- That collides with any entry_number already in use that wasn't
-- assigned through this generator — e.g. the JE-2026-00001 row found to
-- already exist from 2026-05-11, well before the generator's UUID-typing
-- bug was fixed and it could run successfully for the first time. This
-- backfill resyncs acc_journal_sequences to match whatever's actually
-- already in acc_journal_entries, for every tenant and year, not just
-- the one that happened to surface this — any tenant with legacy/seed
-- journal entries has the same latent collision waiting.
--
-- Idempotent: ON CONFLICT ... GREATEST() only ever raises an existing
-- sequence value, never lowers one, so this is safe to run more than
-- once and safe even if some sequence rows are already correct.

INSERT INTO acc_journal_sequences (tenant_id, year, last_seq)
SELECT
    tenant_id,
    CAST(SUBSTRING(entry_number FROM 'JE-(\d{4})-') AS INT) AS year,
    MAX(CAST(SUBSTRING(entry_number FROM 'JE-\d{4}-(\d+)') AS INT)) AS max_seq
FROM acc_journal_entries
WHERE entry_number ~ '^JE-\d{4}-\d+$'
GROUP BY tenant_id, CAST(SUBSTRING(entry_number FROM 'JE-(\d{4})-') AS INT)
ON CONFLICT (tenant_id, year)
DO UPDATE SET last_seq = GREATEST(acc_journal_sequences.last_seq, EXCLUDED.last_seq);