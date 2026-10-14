-- Finance F18: expense policies (limits, receipt rules, age), flags kept on the claim, and reimbursement batches.

CREATE TABLE IF NOT EXISTS expense_policies (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    category          VARCHAR(50) NOT NULL,          -- a claim category, or '*' for every category without its own policy
    max_per_claim     NUMERIC(12,2),
    limit_action      VARCHAR(5),                    -- BLOCK or WARN when max_per_claim is exceeded
    receipt_above     NUMERIC(12,2),                 -- a receipt is required above this amount (0 = always)
    max_age_days      INTEGER,
    updated_by        UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT expense_policies_uq UNIQUE (tenant_id, category),
    CONSTRAINT expense_policies_action_chk CHECK (limit_action IS NULL OR limit_action IN ('BLOCK','WARN'))
);

-- What the policy and duplicate checks raised when the claim was last submitted, shown to the approver.
ALTER TABLE expense_claims ADD COLUMN IF NOT EXISTS policy_flags TEXT;

CREATE TABLE IF NOT EXISTS expense_reimbursement_batches (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    batch_number     VARCHAR(30) NOT NULL,
    status           VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
    total_amount     NUMERIC(14,2) NOT NULL,
    bank_account_id  UUID,
    reference        VARCHAR(100),
    created_by       UUID NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    paid_by          UUID,
    paid_at          TIMESTAMPTZ,
    CONSTRAINT expense_batches_uq UNIQUE (tenant_id, batch_number),
    CONSTRAINT expense_batches_status_chk CHECK (status IN ('DRAFT','PAID','CANCELLED'))
);

CREATE TABLE IF NOT EXISTS expense_reimbursement_batch_items (
    batch_id       UUID NOT NULL REFERENCES expense_reimbursement_batches (id),
    claim_id       UUID NOT NULL,
    tenant_id      UUID NOT NULL,
    employee_name  VARCHAR(200) NOT NULL,
    claim_number   VARCHAR(20) NOT NULL,
    amount         NUMERIC(12,2) NOT NULL,
    PRIMARY KEY (batch_id, claim_id)
);

CREATE INDEX IF NOT EXISTS idx_expense_batch_items_claim ON expense_reimbursement_batch_items (tenant_id, claim_id);
