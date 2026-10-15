-- Weekly and monthly hire; rates held on the hire contract; disputed odometer readings; kilometre charges that remember the
-- readings they came from so they can be recalculated when a reading is corrected.

ALTER TABLE earthmoving_hire_charges DROP CONSTRAINT IF EXISTS earthmoving_hire_charges_basis_check;
ALTER TABLE earthmoving_hire_charges DROP CONSTRAINT IF EXISTS chk_em_hire_basis;
ALTER TABLE earthmoving_hire_charges ADD CONSTRAINT chk_em_hire_basis
    CHECK (basis IN ('DAILY', 'WEEKLY', 'MONTHLY', 'HOURLY', 'KILOMETRE'));

ALTER TABLE earthmoving_hire_charges ADD COLUMN IF NOT EXISTS rate_source VARCHAR(12);
ALTER TABLE earthmoving_hire_charges ADD COLUMN IF NOT EXISTS included_km NUMERIC(10,2);
ALTER TABLE earthmoving_hire_charges ADD COLUMN IF NOT EXISTS start_reading NUMERIC(10,1);
ALTER TABLE earthmoving_hire_charges ADD COLUMN IF NOT EXISTS end_reading NUMERIC(10,1);
ALTER TABLE earthmoving_hire_charges ADD COLUMN IF NOT EXISTS start_reading_id UUID;
ALTER TABLE earthmoving_hire_charges ADD COLUMN IF NOT EXISTS end_reading_id UUID;
ALTER TABLE earthmoving_hire_charges ADD COLUMN IF NOT EXISTS attention VARCHAR(300);
ALTER TABLE earthmoving_hire_charges ADD COLUMN IF NOT EXISTS recalculated_at TIMESTAMP;
ALTER TABLE earthmoving_hire_charges DROP CONSTRAINT IF EXISTS chk_em_hire_rate_source;
ALTER TABLE earthmoving_hire_charges ADD CONSTRAINT chk_em_hire_rate_source
    CHECK (rate_source IS NULL OR rate_source IN ('CONTRACT', 'ASSET', 'AGREED'));
CREATE INDEX IF NOT EXISTS idx_em_hire_charges_km_pending ON earthmoving_hire_charges (tenant_id, asset_id)
    WHERE basis = 'KILOMETRE' AND status = 'PENDING';

-- A correction says which reading it replaces, so a past period sees the corrected figure at the replaced reading's place in time.
ALTER TABLE earthmoving_meter_readings ADD COLUMN IF NOT EXISTS corrects_id UUID;

CREATE TABLE IF NOT EXISTS earthmoving_meter_disputes (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    asset_id        UUID NOT NULL,
    reading_id      UUID NOT NULL,
    status          VARCHAR(12) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CONFIRMED', 'CORRECTED')),
    reason          VARCHAR(500) NOT NULL,
    raised_by       UUID,
    raised_at       TIMESTAMP NOT NULL,
    resolution_note VARCHAR(500),
    resolved_by     UUID,
    resolved_at     TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_em_meter_dispute_open ON earthmoving_meter_disputes (tenant_id, reading_id) WHERE status = 'OPEN';
CREATE INDEX IF NOT EXISTS idx_em_meter_disputes_asset ON earthmoving_meter_disputes (tenant_id, asset_id);

-- The rates agreed in a hire contract. One row per contract and billing basis; the rate of a charge comes from here before the machine's own.
CREATE TABLE IF NOT EXISTS earthmoving_contract_rates (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    contract_id UUID NOT NULL,
    basis       VARCHAR(12) NOT NULL CHECK (basis IN ('DAILY', 'WEEKLY', 'MONTHLY', 'HOURLY', 'KILOMETRE')),
    unit_rate   NUMERIC(12,2) NOT NULL CHECK (unit_rate > 0),
    included_km NUMERIC(10,2) CHECK (included_km IS NULL OR included_km >= 0),
    updated_by  UUID,
    updated_at  TIMESTAMP NOT NULL,
    CONSTRAINT uq_em_contract_rate UNIQUE (tenant_id, contract_id, basis)
);
