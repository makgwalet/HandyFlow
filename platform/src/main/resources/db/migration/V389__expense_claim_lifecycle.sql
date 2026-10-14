-- V389 (finance patch F7, 2026-10-14): the full life of an expense claim.
--
-- Before this, a claim was PENDING the moment it was saved, a rejection was final, approval did not use the approval
-- engine (no thresholds, no second approver), and paying a claim out posted nothing, so Accrued Expenses (2400) only grew.
--   DRAFT      saved, not yet sent for approval; the claimant can change it
--   CANCELLED  withdrawn by the claimant before a decision
--   submitted_at, submission_count   when it was last sent for approval, and how many times (resubmissions after rejection)
--   reimbursed_by, reimbursement_bank_account_id, reimbursement_reference, reimbursement_journal_id   the payout
-- A platform-default approval rule sends every claim to someone with EXPENSES_MANAGE; tenants can add their own rules
-- (for example a second approver above an amount). Existing claims keep their status. Re-runnable.

ALTER TABLE expense_claims DROP CONSTRAINT IF EXISTS expense_claims_status_check;
ALTER TABLE expense_claims ADD CONSTRAINT expense_claims_status_check
    CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'REIMBURSED', 'CANCELLED'));

ALTER TABLE expense_claims ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMP;
ALTER TABLE expense_claims ADD COLUMN IF NOT EXISTS submission_count INT NOT NULL DEFAULT 0;
ALTER TABLE expense_claims ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP;
ALTER TABLE expense_claims ADD COLUMN IF NOT EXISTS reimbursed_by UUID;
ALTER TABLE expense_claims ADD COLUMN IF NOT EXISTS reimbursement_bank_account_id UUID;
ALTER TABLE expense_claims ADD COLUMN IF NOT EXISTS reimbursement_reference VARCHAR(100);
ALTER TABLE expense_claims ADD COLUMN IF NOT EXISTS reimbursement_journal_id UUID;

UPDATE expense_claims SET submitted_at = created_at, submission_count = 1
 WHERE submitted_at IS NULL AND status <> 'DRAFT';

INSERT INTO approval_rules (id, tenant_id, module, entity_type, name, active, priority,
                            conditions, approval_mode, approver_chain, is_platform_default, created_at, updated_at)
SELECT gen_random_uuid(), NULL, 'expenses', 'CLAIM', 'Expense claim approval (platform default)', true, 100,
       NULL, 'SEQUENTIAL', '[{"type":"ROLE","value":"EXPENSES_MANAGE","name":"Expense approver"}]', true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_rules WHERE module = 'expenses' AND entity_type = 'CLAIM' AND is_platform_default);
