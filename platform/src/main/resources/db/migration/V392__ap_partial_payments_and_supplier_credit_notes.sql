-- V392 (finance patch F10, 2026-10-14): Accounts Payable - part payments, supplier credit notes and allocation.
--
-- Before this:
--   * a bill could only be paid whole; there was no record of a payment of its own;
--   * a supplier's credit note had nowhere to go, so a credited bill was still paid in full;
--   * ap_bills_status_check did not allow REJECTED, which the application assigns when an approver rejects a bill, so
--     rejecting a bill failed at the database.
-- Now:
--   ap_bill_payments       every payment made against a bill (its id keys the ledger posting, so a retry cannot pay twice);
--   ap_credit_notes        credit notes received from suppliers (waiting for approval, issued, or rejected); total_amount
--                          minus allocated is the credit still held with the supplier;
--   ap_credit_allocations  which bills a credit note reduced, and by how much;
--   ap_bills.paid_amount / credited_amount   balance = total_amount - paid_amount - credited_amount.
-- A new status PARTIALLY_PAID is a bill that is approved and part settled. Existing PAID bills are backfilled as paid in
-- full, with one payment record carrying the journal they already have. Credit notes of R 5,000 or more go to the approval
-- engine (platform default rule, approver AP_APPROVE). Re-runnable.

ALTER TABLE ap_bills ADD COLUMN IF NOT EXISTS paid_amount     NUMERIC(15,2) NOT NULL DEFAULT 0;
ALTER TABLE ap_bills ADD COLUMN IF NOT EXISTS credited_amount NUMERIC(15,2) NOT NULL DEFAULT 0;

ALTER TABLE ap_bills DROP CONSTRAINT IF EXISTS ap_bills_status_check;
ALTER TABLE ap_bills ADD CONSTRAINT ap_bills_status_check
    CHECK (status IN ('DRAFT','APPROVED','PAID','OVERDUE','CANCELLED','SECOND_APPROVAL','REJECTED','PARTIALLY_PAID'));

CREATE TABLE IF NOT EXISTS ap_bill_payments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    bill_id          UUID NOT NULL REFERENCES ap_bills(id),
    amount           NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    paid_on          DATE NOT NULL,
    bank_account_id  UUID,
    reference        VARCHAR(100),
    batch_id         UUID,
    journal_entry_id UUID,
    paid_by          UUID,
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ap_bill_payments_bill  ON ap_bill_payments (bill_id);
CREATE INDEX IF NOT EXISTS idx_ap_bill_payments_batch ON ap_bill_payments (batch_id) WHERE batch_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS ap_credit_notes (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    credit_note_number VARCHAR(60) NOT NULL,
    supplier_id        UUID,
    supplier_name      VARCHAR(255) NOT NULL,
    bill_id            UUID REFERENCES ap_bills(id),
    category           VARCHAR(40) NOT NULL DEFAULT 'OTHER',
    credit_date        DATE NOT NULL,
    reason             VARCHAR(500) NOT NULL,
    amount             NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    vat_amount         NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (vat_amount >= 0),
    total_amount       NUMERIC(15,2) NOT NULL CHECK (total_amount > 0),
    allocated          NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (allocated >= 0),
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING_APPROVAL'
                       CHECK (status IN ('PENDING_APPROVAL','ISSUED','REJECTED')),
    journal_entry_id   UUID,
    rejection_reason   VARCHAR(500),
    created_by         UUID,
    decided_by         UUID,
    decided_at         TIMESTAMP,
    created_at         TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_ap_credit_not_overallocated CHECK (allocated <= total_amount)
);
-- The same supplier credit note is not captured twice (a rejected one can be captured again).
CREATE UNIQUE INDEX IF NOT EXISTS uq_ap_credit_notes_number
    ON ap_credit_notes (tenant_id, lower(supplier_name), lower(credit_note_number)) WHERE status <> 'REJECTED';
CREATE INDEX IF NOT EXISTS idx_ap_credit_notes_supplier ON ap_credit_notes (tenant_id, lower(supplier_name));

CREATE TABLE IF NOT EXISTS ap_credit_allocations (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    credit_note_id UUID NOT NULL REFERENCES ap_credit_notes(id) ON DELETE CASCADE,
    bill_id        UUID NOT NULL REFERENCES ap_bills(id),
    amount         NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    allocated_by   UUID,
    allocated_at   TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ap_credit_allocations_bill ON ap_credit_allocations (bill_id);
CREATE INDEX IF NOT EXISTS idx_ap_credit_allocations_note ON ap_credit_allocations (credit_note_id);

-- Existing paid bills: paid in full, with the payment journal they already have.
UPDATE ap_bills SET paid_amount = total_amount WHERE status = 'PAID' AND paid_amount = 0;

INSERT INTO ap_bill_payments (tenant_id, bill_id, amount, paid_on, reference, batch_id, journal_entry_id, paid_by, created_at)
SELECT b.tenant_id, b.id, b.total_amount, COALESCE(b.paid_at, b.updated_at, now())::date, b.payment_ref, b.batch_id,
       b.payment_journal_id, b.paid_by, COALESCE(b.paid_at, now())
  FROM ap_bills b
 WHERE b.status = 'PAID' AND b.total_amount > 0
   AND NOT EXISTS (SELECT 1 FROM ap_bill_payments p WHERE p.bill_id = b.id);

-- Credit notes of R 5,000 or more wait for a second person (the engine; never the person who captured it).
INSERT INTO approval_rules (id, tenant_id, module, entity_type, name, active, priority,
                            conditions, approval_mode, approver_chain, is_platform_default, created_at, updated_at)
SELECT gen_random_uuid(), NULL, 'ap', 'CREDIT_NOTE', 'Supplier credit notes of R 5,000 or more (platform default)', true, 100,
       '{"totalAmount": {">=": 5000}}', 'SEQUENTIAL',
       '[{"type":"ROLE","value":"AP_APPROVE","name":"Accounts payable approver"}]', true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_rules WHERE module = 'ap' AND entity_type = 'CREDIT_NOTE' AND is_platform_default);
