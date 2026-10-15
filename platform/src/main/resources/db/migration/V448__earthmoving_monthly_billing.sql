-- Monthly billing run: a deployment can be put on automatic billing (by day, week, month, hour or kilometre), a company can switch
-- the scheduled run on, and every run is recorded. The run only creates pending charges and draft invoices.
ALTER TABLE earthmoving_deployments ADD COLUMN IF NOT EXISTS auto_billing_basis VARCHAR(12);
ALTER TABLE earthmoving_deployments DROP CONSTRAINT IF EXISTS chk_em_deployment_auto_basis;
ALTER TABLE earthmoving_deployments ADD CONSTRAINT chk_em_deployment_auto_basis
    CHECK (auto_billing_basis IS NULL OR auto_billing_basis IN ('DAILY', 'WEEKLY', 'MONTHLY', 'HOURLY', 'KILOMETRE'));
CREATE INDEX IF NOT EXISTS idx_em_deployments_auto_billing ON earthmoving_deployments (tenant_id) WHERE auto_billing_basis IS NOT NULL;

ALTER TABLE earthmoving_settings ADD COLUMN IF NOT EXISTS auto_billing_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS earthmoving_billing_runs (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    period_month    DATE NOT NULL,
    trigger_source  VARCHAR(10) NOT NULL CHECK (trigger_source IN ('MANUAL', 'SCHEDULED')),
    drafts_requested BOOLEAN NOT NULL,
    charges_created INT NOT NULL DEFAULT 0,
    already_billed  INT NOT NULL DEFAULT 0,
    exceptions      INT NOT NULL DEFAULT 0,
    invoices_created INT NOT NULL DEFAULT 0,
    created_by      UUID,
    created_at      TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_em_billing_runs_tenant ON earthmoving_billing_runs (tenant_id, created_at DESC);
-- The schedule runs once per company and month, even if two servers wake up together.
CREATE UNIQUE INDEX IF NOT EXISTS uq_em_billing_runs_scheduled ON earthmoving_billing_runs (tenant_id, period_month) WHERE trigger_source = 'SCHEDULED';
