-- What sort of clause this is. Anything other than STANDARD needs the reviewer's signed-off review attached before it can be published,
-- as do the sensitive categories (see ContractClause.SENSITIVE_CATEGORIES). Existing clauses are STANDARD.
ALTER TABLE contract_clauses ADD COLUMN IF NOT EXISTS clause_kind VARCHAR(12) NOT NULL DEFAULT 'STANDARD';
ALTER TABLE contract_clauses ADD COLUMN IF NOT EXISTS source_reference VARCHAR(500);
ALTER TABLE contract_clauses ADD CONSTRAINT chk_clause_kind CHECK (clause_kind IN ('STANDARD', 'CUSTOM', 'REGULATORY', 'SENSITIVE', 'SOURCED'));
