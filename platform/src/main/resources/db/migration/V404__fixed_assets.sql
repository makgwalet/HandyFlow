-- Finance F25: fixed-asset register, monthly depreciation runs (as draft journals), disposals.
CREATE TABLE acc_fixed_assets (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL REFERENCES tenants(id),
    asset_number         VARCHAR(20)  NOT NULL,
    name                 VARCHAR(160) NOT NULL,
    description          TEXT,
    serial_number        VARCHAR(80),
    location             VARCHAR(120),
    cost_account_id      UUID NOT NULL REFERENCES acc_accounts(id),
    accumulated_account_id UUID NOT NULL REFERENCES acc_accounts(id),
    expense_account_id   UUID NOT NULL REFERENCES acc_accounts(id),
    cost_centre_id       UUID REFERENCES acc_cost_centres(id),
    acquired_on          DATE NOT NULL,
    cost                 NUMERIC(15,2) NOT NULL CHECK (cost > 0),
    residual_value       NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (residual_value >= 0),
    method               VARCHAR(20) NOT NULL CHECK (method IN ('STRAIGHT_LINE','REDUCING_BALANCE')),
    life_months          INT CHECK (life_months IS NULL OR life_months BETWEEN 1 AND 600),
    rate_percent         NUMERIC(6,3) CHECK (rate_percent IS NULL OR (rate_percent > 0 AND rate_percent <= 100)),
    status               VARCHAR(12) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DISPOSED')),
    disposed_on          DATE,
    disposal_proceeds    NUMERIC(15,2),
    disposal_journal_id  UUID REFERENCES acc_journal_entries(id),
    created_by           UUID,
    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_fa_residual_below_cost CHECK (residual_value < cost),
    CONSTRAINT chk_fa_method_fields CHECK (
        (method = 'STRAIGHT_LINE' AND life_months IS NOT NULL) OR (method = 'REDUCING_BALANCE' AND rate_percent IS NOT NULL))
);
CREATE UNIQUE INDEX uq_acc_fixed_assets_number ON acc_fixed_assets (tenant_id, asset_number);
CREATE INDEX idx_acc_fixed_assets_status ON acc_fixed_assets (tenant_id, status);

CREATE TABLE acc_depreciation_runs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id),
    period_start     DATE NOT NULL,
    journal_entry_id UUID NOT NULL REFERENCES acc_journal_entries(id),
    total            NUMERIC(15,2) NOT NULL,
    created_by       UUID,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_dep_run_first_of_month CHECK (EXTRACT(DAY FROM period_start) = 1)
);
CREATE UNIQUE INDEX uq_acc_depreciation_runs_period ON acc_depreciation_runs (tenant_id, period_start);

CREATE TABLE acc_depreciation_lines (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    run_id    UUID NOT NULL REFERENCES acc_depreciation_runs(id),
    asset_id  UUID NOT NULL REFERENCES acc_fixed_assets(id),
    amount    NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    UNIQUE (run_id, asset_id)
);
CREATE INDEX idx_acc_depreciation_lines_asset ON acc_depreciation_lines (asset_id);
