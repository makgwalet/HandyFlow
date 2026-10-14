-- V386 (finance patch F4, 2026-10-14): postings that failed are kept, shown and retried instead of only logged.
--
-- Before this, when an invoice, payment, expense claim, POS session or module invoice failed to post (a missing account,
-- a lock, a database error), the business record stood and the ledger silently missed it; the only trace was a log line.
-- One row per business event (source_key, as on acc_journal_entries) while it is open; a later failure of the same event
-- adds an attempt to that row. RESOLVED when it posts (by retry, or by the business event posting later); DISMISSED when a
-- person decides it should not post (with a note). request_json is the journal that was attempted, so it can be retried;
-- null when the failure happened before a journal could be built (for example a missing account). Re-runnable.

CREATE TABLE IF NOT EXISTS acc_posting_failures (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    source_key       VARCHAR(200) NOT NULL,
    summary          VARCHAR(300) NOT NULL,
    request_json     TEXT,
    reason           VARCHAR(1000) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED')),
    attempts         INT NOT NULL DEFAULT 1,
    first_failed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_failed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at      TIMESTAMPTZ,
    resolved_by      UUID,
    resolution_note  VARCHAR(500),
    journal_entry_id UUID REFERENCES acc_journal_entries(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_acc_posting_failure_open
    ON acc_posting_failures (tenant_id, source_key) WHERE status = 'OPEN';
CREATE INDEX IF NOT EXISTS idx_acc_posting_failures_tenant_status
    ON acc_posting_failures (tenant_id, status, last_failed_at DESC);
