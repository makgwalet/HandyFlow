-- V391 (finance patch F9, 2026-10-14): credit notes above a limit need approval; customer credit can be refunded.
--
-- With refunds, a credit note becomes a way for money to leave the business, so:
--   credit_notes.status   PENDING_APPROVAL until approved through the approval engine, then ISSUED (applied to the
--                         invoice, emailed and posted), or REJECTED. Existing credit notes are ISSUED.
--   created_by, decided_by, decided_at, rejection_reason   who raised it and who decided.
--   Platform-default approval rule: credit notes of R 5,000 or more (incl. VAT) need someone with CREDIT_NOTE_APPROVE.
--   Below that, or where a tenant's own rule says so, they are issued at once.
--   customer_refunds      money paid back to a customer out of their credit (a receipt's unused amount), from a bank
--                         account; posts Dr Accounts Receivable, Cr the bank. A receipt's "allocated" now counts both
--                         allocations to invoices and refunds.
-- New permissions CREDIT_NOTE_APPROVE and INVOICE_REFUND go to ADMIN and OWNER only. Re-runnable.

ALTER TABLE credit_notes ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ISSUED';
ALTER TABLE credit_notes DROP CONSTRAINT IF EXISTS credit_notes_status_check;
ALTER TABLE credit_notes ADD CONSTRAINT credit_notes_status_check CHECK (status IN ('PENDING_APPROVAL', 'ISSUED', 'REJECTED'));
ALTER TABLE credit_notes ADD COLUMN IF NOT EXISTS created_by UUID;
ALTER TABLE credit_notes ADD COLUMN IF NOT EXISTS decided_by UUID;
ALTER TABLE credit_notes ADD COLUMN IF NOT EXISTS decided_at TIMESTAMPTZ;
ALTER TABLE credit_notes ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(500);
ALTER TABLE credit_notes ALTER COLUMN issued_at DROP NOT NULL;
ALTER TABLE credit_notes ALTER COLUMN issued_at DROP DEFAULT;

CREATE TABLE IF NOT EXISTS customer_refunds (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    refund_number   VARCHAR(40) NOT NULL,
    receipt_id      UUID NOT NULL REFERENCES customer_receipts(id),
    customer_id     UUID,
    amount          NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    refunded_on     DATE NOT NULL,
    bank_account_id UUID NOT NULL,
    reference       VARCHAR(100),
    reason          VARCHAR(500) NOT NULL,
    created_by      UUID,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_customer_refunds_number UNIQUE (tenant_id, refund_number)
);
CREATE INDEX IF NOT EXISTS idx_customer_refunds_receipt ON customer_refunds (receipt_id);
CREATE INDEX IF NOT EXISTS idx_customer_refunds_customer ON customer_refunds (tenant_id, customer_id);

INSERT INTO approval_rules (id, tenant_id, module, entity_type, name, active, priority,
                            conditions, approval_mode, approver_chain, is_platform_default, created_at, updated_at)
SELECT gen_random_uuid(), NULL, 'invoicing', 'CREDIT_NOTE', 'Credit notes of R 5,000 or more (platform default)', true, 100,
       '{"totalAmount": {">=": 5000}}', 'SEQUENTIAL',
       '[{"type":"ROLE","value":"CREDIT_NOTE_APPROVE","name":"Credit note approver"}]', true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_rules WHERE module = 'invoicing' AND entity_type = 'CREDIT_NOTE' AND is_platform_default);

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'CREDIT_NOTE_APPROVE', 'Approve credit notes above the approval limit (never your own)'),
    (gen_random_uuid(), 'INVOICE_REFUND',      'Pay customer credit back to the customer')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN ('CREDIT_NOTE_APPROVE', 'INVOICE_REFUND')
 WHERE r.name IN ('ADMIN', 'OWNER')
ON CONFLICT DO NOTHING;
