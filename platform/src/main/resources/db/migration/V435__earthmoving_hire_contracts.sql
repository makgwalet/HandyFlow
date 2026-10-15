-- A hire deployment can reference the contract that covers it (held in the Contracting module), and each company can set the
-- hire value from which a signed contract is expected. No value set means contracts are never expected.
ALTER TABLE earthmoving_deployments ADD COLUMN IF NOT EXISTS contract_id UUID;
CREATE INDEX IF NOT EXISTS idx_em_deployments_contract ON earthmoving_deployments (tenant_id, contract_id) WHERE contract_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS earthmoving_settings (
    tenant_id          UUID PRIMARY KEY,
    contract_threshold NUMERIC(14,2) CHECK (contract_threshold IS NULL OR contract_threshold >= 0),
    updated_by         UUID,
    updated_at         TIMESTAMP NOT NULL DEFAULT now()
);
