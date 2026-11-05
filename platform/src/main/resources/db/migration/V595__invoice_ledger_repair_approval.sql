-- INV-01 repair needs an approval before anything is posted. The approval is for the exact list of invoices (and
-- amounts) that was previewed: if that list changes, the approval no longer applies and a new one is needed.
CREATE TABLE IF NOT EXISTS invoice_ledger_repair_approvals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL,
    preview_hash    VARCHAR(64)  NOT NULL,
    invoice_count   INTEGER      NOT NULL,
    total_amount    NUMERIC(18,2) NOT NULL,
    original_dated  INTEGER      NOT NULL,
    prior_period    INTEGER      NOT NULL,
    unpostable      INTEGER      NOT NULL,
    reason          VARCHAR(500) NOT NULL,
    approved_by     UUID         NOT NULL,
    approved_at     TIMESTAMP    NOT NULL DEFAULT now(),
    expires_at      TIMESTAMP    NOT NULL,
    executed_by     UUID,
    executed_at     TIMESTAMP,
    posted_count    INTEGER
);
CREATE INDEX IF NOT EXISTS idx_invoice_ledger_repair_approvals_tenant ON invoice_ledger_repair_approvals (tenant_id, approved_at DESC);
