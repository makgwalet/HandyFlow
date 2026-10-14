-- V387 (finance patch F5, 2026-10-14): a lock date for the books.
--
-- Before this, only VAT periods could be closed, and posting a journal checked no closed period, so entries could be dated
-- into a month already reported. Now each tenant has one lock date: no journal dated on or before it can be created or
-- posted, by a person or by the system. Moving it forward closes more of the books (ACCOUNTING_ADMIN); moving it back
-- reopens them and needs ACCOUNTING_PERIOD_REOPEN and a reason. Every change is kept in acc_lock_date_events.
-- New permission ACCOUNTING_PERIOD_REOPEN goes to ADMIN and OWNER roles only. Re-runnable.

CREATE TABLE IF NOT EXISTS acc_lock_dates (
    tenant_id      UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    locked_through DATE,
    updated_by     UUID,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS acc_lock_date_events (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    action    VARCHAR(10) NOT NULL CHECK (action IN ('LOCK', 'REOPEN')),
    from_date DATE,
    to_date   DATE,
    note      VARCHAR(500),
    by_user   UUID,
    at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_acc_lock_date_events_tenant ON acc_lock_date_events (tenant_id, at DESC);

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'ACCOUNTING_PERIOD_REOPEN', 'Reopen closed accounting periods (move the lock date back)')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name = 'ACCOUNTING_PERIOD_REOPEN'
WHERE r.name IN ('ADMIN', 'OWNER')
ON CONFLICT DO NOTHING;
