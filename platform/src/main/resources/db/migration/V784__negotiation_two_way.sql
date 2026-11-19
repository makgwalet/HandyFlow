-- Two-way negotiation: staff can propose too, and either side can accept, reject or counter. Every round stays as its own row.
-- Existing rows are counterparty proposals, round 1, with no parent.
ALTER TABLE contract_negotiation_proposals ADD COLUMN IF NOT EXISTS author_type VARCHAR(12) NOT NULL DEFAULT 'COUNTERPARTY';
ALTER TABLE contract_negotiation_proposals ADD COLUMN IF NOT EXISTS author_user_id UUID;
ALTER TABLE contract_negotiation_proposals ADD COLUMN IF NOT EXISTS parent_proposal_id UUID REFERENCES contract_negotiation_proposals (id);
ALTER TABLE contract_negotiation_proposals ADD COLUMN IF NOT EXISTS round_no INTEGER NOT NULL DEFAULT 1;
ALTER TABLE contract_negotiation_proposals ADD COLUMN IF NOT EXISTS decided_by_party_id UUID;
ALTER TABLE contract_negotiation_proposals ADD CONSTRAINT chk_negotiation_proposal_author CHECK (author_type IN ('STAFF', 'COUNTERPARTY'));
CREATE INDEX IF NOT EXISTS idx_negotiation_proposals_parent ON contract_negotiation_proposals (parent_proposal_id);
