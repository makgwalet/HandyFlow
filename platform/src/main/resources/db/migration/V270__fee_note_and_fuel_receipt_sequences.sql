-- V270__fee_note_and_fuel_receipt_sequences.sql
-- FIX (P0 backlog, items 1.5 and 1.6): the exact same race-condition bug
-- V74__journal_sequences.sql already diagnosed and fixed for journal
-- entries -- SELECT COUNT(*) + 1 is not atomic; two concurrent requests in
-- the same year read the same count and generate the same number -- was
-- still live in two more places: FeeNoteNumberGenerator (Accountant
-- module) and ReceiptNumberGenerator (Fuel module). Same fix shape as
-- V74, applied to both.
--
-- Fuel's ReceiptNumberGenerator had a second, separate bug beyond the
-- race condition: its COUNT query had no tenant_id filter at all, so
-- receipt sequence numbers were being computed across every tenant on
-- the platform combined, not per-tenant. fuel_receipt_sequences below
-- is tenant-scoped from the start, same as acc_fee_note_sequences and
-- the existing acc_journal_sequences.

CREATE TABLE acc_fee_note_sequences (
    tenant_id  UUID    NOT NULL,
    year       INT     NOT NULL,
    last_seq   BIGINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, year)
);

COMMENT ON TABLE acc_fee_note_sequences IS
    'Atomic sequence counter for fee note numbers (FN-YYYY-NNNNN) per tenant per year. '
    'Uses UPDATE … RETURNING which holds a row lock, preventing duplicate fee note numbers.';

CREATE TABLE fuel_receipt_sequences (
    tenant_id  UUID    NOT NULL,
    year       INT     NOT NULL,
    last_seq   BIGINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, year)
);

COMMENT ON TABLE fuel_receipt_sequences IS
    'Atomic, tenant-scoped sequence counter for fuel delivery receipt numbers (FDR-YYYY-NNNNN). '
    'Replaces a prior implementation that both raced under concurrent completions AND counted '
    'across all tenants combined rather than per-tenant.';
