-- V388 (finance patch F6, 2026-10-14): separation of duties in Accounts Payable.
--
-- Before this, AP_MANAGE (or BILLING_MANAGE) let one person capture a bill, approve it, change the supplier's bank
-- details and release the payment. Now:
--   AP_APPROVE           approve bills (the approval engine's default AP rule now asks for it)
--   AP_PAY               pay a bill, release (submit) a payment batch, confirm a batch paid
--   AP_SUPPLIER_BANKING  add, change, check and remove supplier bank details
-- Granted to every role that holds AP_MANAGE or BILLING_MANAGE today, and to ADMIN and OWNER, so nobody loses access;
-- the separation comes from the "a different person" checks in ApService, which now also apply.
-- Supplier bank details carry who changed them and who checked them. Details that existed before this are treated as
-- checked (verification_note says so), so payments already set up keep working. Re-runnable.

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'AP_APPROVE',          'Approve supplier bills (never a bill you captured)'),
    (gen_random_uuid(), 'AP_PAY',              'Pay bills and release payment batches (never your own)'),
    (gen_random_uuid(), 'AP_SUPPLIER_BANKING', 'Add, change and check supplier bank details')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT r.id, np.id
FROM roles r
JOIN permissions np ON np.name IN ('AP_APPROVE', 'AP_PAY', 'AP_SUPPLIER_BANKING')
WHERE r.name IN ('ADMIN', 'OWNER')
   OR EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
              WHERE rp.role_id = r.id AND p.name IN ('AP_MANAGE', 'BILLING_MANAGE'))
ON CONFLICT DO NOTHING;

-- The platform's default AP approval rule asked for AP_MANAGE at each step; it now asks for AP_APPROVE.
UPDATE approval_rules
   SET approver_chain = REPLACE(approver_chain::text, '"AP_MANAGE"', '"AP_APPROVE"')::jsonb, updated_at = now()
 WHERE is_platform_default AND module = 'ap' AND entity_type = 'BILL'
   AND approver_chain::text LIKE '%"AP_MANAGE"%';

ALTER TABLE ap_supplier_banking ADD COLUMN IF NOT EXISTS changed_by UUID;
ALTER TABLE ap_supplier_banking ADD COLUMN IF NOT EXISTS changed_at TIMESTAMPTZ;
ALTER TABLE ap_supplier_banking ADD COLUMN IF NOT EXISTS verified_by UUID;
ALTER TABLE ap_supplier_banking ADD COLUMN IF NOT EXISTS verified_at TIMESTAMPTZ;
ALTER TABLE ap_supplier_banking ADD COLUMN IF NOT EXISTS verification_note VARCHAR(200);

UPDATE ap_supplier_banking
   SET changed_by = COALESCE(changed_by, created_by), changed_at = COALESCE(changed_at, created_at),
       verified_at = COALESCE(verified_at, now()),
       verification_note = COALESCE(verification_note, 'Entered before second-person checks (2026-10-14)')
 WHERE verified_at IS NULL AND changed_at IS NULL;
