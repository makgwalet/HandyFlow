-- A contract can point at the business record it is for (a hire deployment, say) and at the contract it amends.
-- Nothing is required: every existing contract keeps working unchanged.
ALTER TABLE contracts ADD COLUMN IF NOT EXISTS parent_contract_id UUID;
ALTER TABLE contracts ADD COLUMN IF NOT EXISTS ref_type VARCHAR(40);
ALTER TABLE contracts ADD COLUMN IF NOT EXISTS ref_id UUID;
CREATE INDEX IF NOT EXISTS idx_contracts_ref ON contracts (tenant_id, ref_type, ref_id) WHERE ref_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_contracts_parent ON contracts (tenant_id, parent_contract_id) WHERE parent_contract_id IS NOT NULL;
