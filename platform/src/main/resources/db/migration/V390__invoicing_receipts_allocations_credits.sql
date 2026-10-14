-- V390 (finance patch F8, 2026-10-14): money received, how it settles invoices, and customer credit.
--
-- Before this:
--   * a payment was only a number added to one invoice's amount_paid: it could not be spread over several invoices and
--     had no record of its own;
--   * paying more than an invoice owed marked it OVERPAID with credit_amount, and that credit could not be used;
--   * credit notes did not reduce what an invoice owed, so credited invoices stayed open, were aged and were chased.
-- Now:
--   customer_receipts     money received (PAYMENT), the part of a credit note beyond what its invoice still owed
--                         (CREDIT_NOTE), or an old overpayment carried onto the customer's account (CARRIED_OVERPAYMENT).
--                         amount - allocated is the customer's credit still available.
--   receipt_allocations   which invoices a receipt settled, and by how much.
--   invoices.credited_amount   credit notes applied to the invoice; balance = total - amount_paid - credited_amount.
-- Existing data: credit notes are applied to their invoices (up to what each still owed; any rest becomes the customer's
-- credit); OVERPAID invoices are settled at their total and the extra becomes the customer's credit. Statuses are
-- recalculated. New permissions INVOICE_PAYMENT and CREDIT_NOTE_ISSUE go to every role holding INVOICE_CREATE, and to
-- ADMIN and OWNER. Re-runnable.

CREATE TABLE IF NOT EXISTS customer_receipts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    receipt_number  VARCHAR(40) NOT NULL,
    customer_id     UUID,
    kind            VARCHAR(30) NOT NULL CHECK (kind IN ('PAYMENT', 'CREDIT_NOTE', 'CARRIED_OVERPAYMENT')),
    source_id       UUID,
    amount          NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    allocated       NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (allocated >= 0),
    received_on     DATE NOT NULL,
    method          VARCHAR(30),
    reference       VARCHAR(100),
    bank_account_id UUID,
    notes           VARCHAR(500),
    created_by      UUID,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_receipt_not_overallocated CHECK (allocated <= amount),
    CONSTRAINT uq_customer_receipts_number UNIQUE (tenant_id, receipt_number)
);
CREATE INDEX IF NOT EXISTS idx_customer_receipts_customer ON customer_receipts (tenant_id, customer_id, received_on);
CREATE UNIQUE INDEX IF NOT EXISTS uq_customer_receipts_source ON customer_receipts (tenant_id, kind, source_id) WHERE kind <> 'PAYMENT';

CREATE TABLE IF NOT EXISTS receipt_allocations (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    receipt_id   UUID NOT NULL REFERENCES customer_receipts(id) ON DELETE CASCADE,
    invoice_id   UUID NOT NULL REFERENCES invoices(id),
    amount       NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    allocated_by UUID,
    allocated_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_receipt_allocations_invoice ON receipt_allocations (invoice_id);
CREATE INDEX IF NOT EXISTS idx_receipt_allocations_receipt ON receipt_allocations (receipt_id);

ALTER TABLE invoices ADD COLUMN IF NOT EXISTS credited_amount NUMERIC(15,2) NOT NULL DEFAULT 0;

-- Existing credit notes: apply to their invoice up to what it still owed; the rest becomes the customer's credit.
CREATE TEMP TABLE IF NOT EXISTS v390_credits AS
SELECT i.id AS invoice_id, i.tenant_id, i.customer_id,
       COALESCE(SUM(cn.total), 0) AS credit_total,
       GREATEST(i.total - i.amount_paid, 0) AS owed
  FROM invoices i JOIN credit_notes cn ON cn.invoice_id = i.id
 WHERE i.credited_amount = 0 AND i.deleted_at IS NULL
 GROUP BY i.id, i.tenant_id, i.customer_id, i.total, i.amount_paid;

UPDATE invoices i SET credited_amount = LEAST(c.credit_total, c.owed)
  FROM v390_credits c WHERE c.invoice_id = i.id;

INSERT INTO customer_receipts (tenant_id, receipt_number, customer_id, kind, source_id, amount, received_on, notes)
SELECT c.tenant_id, 'RC-CN-' || left(replace(c.invoice_id::text, '-', ''), 12), c.customer_id, 'CREDIT_NOTE', c.invoice_id,
       c.credit_total - LEAST(c.credit_total, c.owed), CURRENT_DATE,
       'Credit notes beyond what the invoice still owed (moved to the customer''s account on 2026-10-14)'
  FROM v390_credits c
 WHERE c.customer_id IS NOT NULL AND c.credit_total - LEAST(c.credit_total, c.owed) > 0
ON CONFLICT DO NOTHING;

DROP TABLE IF EXISTS v390_credits;

-- Old overpayments: settle the invoice at its total; the extra becomes the customer's credit.
INSERT INTO customer_receipts (tenant_id, receipt_number, customer_id, kind, source_id, amount, received_on, notes)
SELECT i.tenant_id, 'RC-OP-' || left(replace(i.id::text, '-', ''), 12), i.customer_id, 'CARRIED_OVERPAYMENT', i.id,
       i.amount_paid - i.total, CURRENT_DATE,
       'Overpayment on invoice ' || i.invoice_number || ' moved to the customer''s account on 2026-10-14'
  FROM invoices i
 WHERE i.status = 'OVERPAID' AND i.customer_id IS NOT NULL AND i.amount_paid > i.total AND i.deleted_at IS NULL
ON CONFLICT DO NOTHING;

UPDATE invoices SET amount_paid = total, credit_amount = 0, status = 'PAID'
 WHERE status = 'OVERPAID' AND customer_id IS NOT NULL AND amount_paid > total AND deleted_at IS NULL;

-- Statuses from what is now settled.
UPDATE invoices SET status = 'PAID'
 WHERE status IN ('ISSUED', 'PARTIALLY_PAID', 'OVERDUE') AND deleted_at IS NULL
   AND amount_paid + credited_amount >= total AND credited_amount > 0;
UPDATE invoices SET status = 'PARTIALLY_PAID'
 WHERE status IN ('ISSUED', 'OVERDUE') AND deleted_at IS NULL
   AND credited_amount > 0 AND amount_paid + credited_amount < total;

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'INVOICE_PAYMENT',   'Record customer payments and allocate them or customer credit to invoices'),
    (gen_random_uuid(), 'CREDIT_NOTE_ISSUE', 'Issue credit notes against invoices')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT r.id, np.id
  FROM roles r
  JOIN permissions np ON np.name IN ('INVOICE_PAYMENT', 'CREDIT_NOTE_ISSUE')
 WHERE r.name IN ('ADMIN', 'OWNER')
    OR EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
               WHERE rp.role_id = r.id AND p.name = 'INVOICE_CREATE')
ON CONFLICT DO NOTHING;
