-- V62__recreate_acc_journal_lines.sql
--
-- The accounting module has two journal entities:
--   AccJournalEntry  → acc_journal_entries  (header: date, description, status)
--   AccJournalLine   → acc_journal_lines    (lines:  debit/credit per account)
--
-- acc_journal_lines was lost during the V58-V61 migration sequence.
-- This migration recreates it with the correct structure inferred from
-- AccJournalLine.debit() / AccJournalLine.credit() factory methods in
-- AccountingService: tenantId, entryId (FK → acc_journal_entries), accountId,
-- debit, credit, description, lineOrder.
--
-- Uses IF NOT EXISTS — safe to re-run.

CREATE TABLE IF NOT EXISTS acc_journal_lines (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    entry_id        UUID NOT NULL REFERENCES acc_journal_entries(id) ON DELETE CASCADE,
    account_id      UUID NOT NULL REFERENCES acc_accounts(id),
    description     VARCHAR(500),
    debit_amount    NUMERIC(15,2) NOT NULL DEFAULT 0,
    credit_amount   NUMERIC(15,2) NOT NULL DEFAULT 0,
    line_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_acc_jl_debit_credit CHECK (
        (debit_amount > 0 AND credit_amount = 0) OR
        (credit_amount > 0 AND debit_amount = 0)
    )
);

CREATE INDEX IF NOT EXISTS idx_acc_journal_lines_entry
    ON acc_journal_lines(entry_id);

CREATE INDEX IF NOT EXISTS idx_acc_journal_lines_account
    ON acc_journal_lines(account_id);
