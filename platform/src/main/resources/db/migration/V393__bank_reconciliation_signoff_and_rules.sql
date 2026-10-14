-- V393 (finance patch F12, 2026-10-14): bank reconciliation you can sign off, and rules that remember how to categorise.
--
-- Before this, a statement line was matched or given a new journal one at a time, and nothing showed what was still
-- unmatched on either side, whether the ledger agreed with the statement, who matched what, or whether anyone had
-- checked the work.
-- Now:
--   acc_bank_transactions.reconciled_by / reconcile_kind / rule_id   who matched a line, how (MATCHED to an existing
--                          journal, NEW_JOURNAL made from the line, RULE applied) and which rule;
--   acc_bank_reconciliations   a bank account reconciled to a date and signed off by a person other than whoever made the
--                          journals in it (when another person can). Reopening needs a reason and is kept;
--   acc_bank_rules         reusable rules: when a statement line's text, direction and amount fit, book it to an account.
-- New permission ACCOUNTING_RECON_SIGNOFF goes to ADMIN, OWNER and every role holding ACCOUNTING_ADMIN. Re-runnable.

ALTER TABLE acc_bank_transactions ADD COLUMN IF NOT EXISTS reconciled_by  UUID;
ALTER TABLE acc_bank_transactions ADD COLUMN IF NOT EXISTS reconcile_kind VARCHAR(20);
ALTER TABLE acc_bank_transactions ADD COLUMN IF NOT EXISTS rule_id        UUID;
ALTER TABLE acc_bank_transactions DROP CONSTRAINT IF EXISTS acc_bank_transactions_reconcile_kind_check;
ALTER TABLE acc_bank_transactions ADD CONSTRAINT acc_bank_transactions_reconcile_kind_check
    CHECK (reconcile_kind IS NULL OR reconcile_kind IN ('MATCHED', 'NEW_JOURNAL', 'RULE'));

CREATE TABLE IF NOT EXISTS acc_bank_rules (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name             VARCHAR(100) NOT NULL,
    bank_account_id  UUID,
    direction        VARCHAR(5) NOT NULL DEFAULT 'ANY' CHECK (direction IN ('IN', 'OUT', 'ANY')),
    match_text       VARCHAR(200),
    match_field      VARCHAR(12) NOT NULL DEFAULT 'EITHER' CHECK (match_field IN ('DESCRIPTION', 'REFERENCE', 'EITHER')),
    min_amount       NUMERIC(15,2),
    max_amount       NUMERIC(15,2),
    other_account_id UUID NOT NULL,
    description      VARCHAR(200),
    priority         INT NOT NULL DEFAULT 100,
    active           BOOLEAN NOT NULL DEFAULT true,
    times_applied    INT NOT NULL DEFAULT 0,
    created_by       UUID,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_acc_bank_rule_has_condition CHECK (match_text IS NOT NULL OR min_amount IS NOT NULL OR max_amount IS NOT NULL),
    CONSTRAINT chk_acc_bank_rule_range CHECK (min_amount IS NULL OR max_amount IS NULL OR min_amount <= max_amount)
);
CREATE INDEX IF NOT EXISTS idx_acc_bank_rules_tenant ON acc_bank_rules (tenant_id, active, priority);

CREATE TABLE IF NOT EXISTS acc_bank_reconciliations (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    bank_account_id      UUID NOT NULL,
    period_end           DATE NOT NULL,
    statement_balance    NUMERIC(15,2) NOT NULL,
    book_balance         NUMERIC(15,2) NOT NULL,
    unmatched_book_count INT NOT NULL DEFAULT 0,
    unmatched_book_net   NUMERIC(15,2) NOT NULL DEFAULT 0,
    difference           NUMERIC(15,2) NOT NULL DEFAULT 0,
    note                 VARCHAR(500),
    status               VARCHAR(12) NOT NULL DEFAULT 'SIGNED_OFF' CHECK (status IN ('SIGNED_OFF', 'REOPENED')),
    signed_by            UUID,
    signed_at            TIMESTAMP NOT NULL DEFAULT now(),
    reopened_by          UUID,
    reopened_at          TIMESTAMP,
    reopen_reason        VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS idx_acc_bank_recs_account ON acc_bank_reconciliations (tenant_id, bank_account_id, period_end DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_acc_bank_recs_signed ON acc_bank_reconciliations (bank_account_id, period_end) WHERE status = 'SIGNED_OFF';

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'ACCOUNTING_RECON_SIGNOFF', 'Sign off a bank reconciliation, and reopen one (never for journals you made yourself)')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT r.id, p.id FROM roles r JOIN permissions p ON p.name = 'ACCOUNTING_RECON_SIGNOFF'
 WHERE r.name IN ('ADMIN', 'OWNER')
    OR EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions pa ON pa.id = rp.permission_id
                WHERE rp.role_id = r.id AND pa.name = 'ACCOUNTING_ADMIN')
ON CONFLICT DO NOTHING;
