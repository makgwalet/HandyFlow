-- A refund pays money out of the business, so a second authorised person approves it (maker/checker).
-- PENDING: asked for, the credit is held back, nothing is posted yet.
-- ISSUED:  approved (or issued directly because nobody else could approve; that case is recorded in audit_events).
-- REJECTED: not paid, the held credit is released.
-- Every refund that exists today was issued directly, so the default is ISSUED.
ALTER TABLE customer_refunds ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ISSUED';
ALTER TABLE customer_refunds ADD COLUMN IF NOT EXISTS decided_by UUID;
ALTER TABLE customer_refunds ADD COLUMN IF NOT EXISTS decided_at TIMESTAMP;
ALTER TABLE customer_refunds ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(500);
ALTER TABLE customer_refunds DROP CONSTRAINT IF EXISTS chk_customer_refunds_status;
ALTER TABLE customer_refunds ADD CONSTRAINT chk_customer_refunds_status CHECK (status IN ('PENDING', 'ISSUED', 'REJECTED'));
CREATE INDEX IF NOT EXISTS idx_customer_refunds_pending ON customer_refunds (tenant_id) WHERE status = 'PENDING';
