-- V74__journal_sequences.sql
-- WHY? The JournalNumberGenerator previously used SELECT COUNT(*) + 1 which is not atomic.
-- Two concurrent requests in the same year would generate the same sequence number and
-- one would fail with a UNIQUE constraint violation on (tenant_id, entry_number).
-- This table + UPDATE … RETURNING pattern is atomic — the row lock prevents concurrent reads.

CREATE TABLE acc_journal_sequences (
    tenant_id  UUID    NOT NULL,
    year       INT     NOT NULL,
    last_seq   BIGINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, year)
);

COMMENT ON TABLE acc_journal_sequences IS
    'Atomic sequence counter for journal entry numbers per tenant per year. '
    'Uses UPDATE … RETURNING which holds a row lock, preventing duplicate JE numbers.';
