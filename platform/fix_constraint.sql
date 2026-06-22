-- ══════════════════════════════════════════════════════════════════════════════
-- FIX 1: Add OVERPAID to the invoices status check constraint
-- Run this in psql / DBeaver against your handyflow DB
-- ══════════════════════════════════════════════════════════════════════════════

ALTER TABLE invoices DROP CONSTRAINT IF EXISTS chk_invoices_status;

ALTER TABLE invoices ADD CONSTRAINT chk_invoices_status
    CHECK (status IN (
        'DRAFT',
        'ISSUED',
        'PARTIALLY_PAID',
        'PAID',
        'OVERPAID',
        'OVERDUE',
        'CANCELLED'
    ));

-- Verify
SELECT conname, pg_get_constraintdef(oid)
FROM   pg_constraint
WHERE  conrelid = 'invoices'::regclass
AND    conname  = 'chk_invoices_status';
