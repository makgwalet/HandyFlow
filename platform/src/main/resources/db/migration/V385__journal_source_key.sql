-- V385 (finance patch F3, 2026-10-14): one journal per business event.
--
-- Before this, journals posted for business events (invoice issued, payment received, bill approved or paid, expense
-- claim approved, credit note, POS session, fee notes and module invoices) carried no link to the event, so an event
-- handled twice (a redelivered application event, a retried approval, a bill paid alone and again in a batch) posted twice.
--   source_key  "<module>:<event>:<id>", set by AccountingFacade.postOnce. Null for journals people make.
-- At most one live journal per key per tenant. A reversed journal no longer counts, so a corrected event can post again.
-- Existing rows keep a null key. Re-runnable.

ALTER TABLE acc_journal_entries ADD COLUMN IF NOT EXISTS source_key VARCHAR(200);

CREATE UNIQUE INDEX IF NOT EXISTS uq_acc_journal_source_key
    ON acc_journal_entries (tenant_id, source_key)
    WHERE source_key IS NOT NULL AND deleted_at IS NULL AND status <> 'REVERSED';
