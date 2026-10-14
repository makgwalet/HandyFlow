-- V394 (finance patch F13, 2026-10-14): month-end close checklist.
-- The checks the system can make itself are worked out when the checklist is opened. The steps only a person can vouch for
-- (accruals, payroll, depreciation, debtors, creditors) are recorded here, one row per month and step, with who confirmed
-- it. Closing the month uses the existing lock date (V387/F5). No new permission: confirming needs ACCOUNTING_MANAGE and
-- closing needs ACCOUNTING_ADMIN, as the lock date already does. Re-runnable.

CREATE TABLE IF NOT EXISTS acc_close_confirmations (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    period_end   DATE NOT NULL,
    step_key     VARCHAR(30) NOT NULL,
    note         VARCHAR(500),
    confirmed_by UUID,
    confirmed_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_acc_close_confirmation UNIQUE (tenant_id, period_end, step_key)
);
CREATE INDEX IF NOT EXISTS idx_acc_close_confirmations_period ON acc_close_confirmations (tenant_id, period_end);
