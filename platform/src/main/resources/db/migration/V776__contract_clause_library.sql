-- Clause library. Clauses are written and approved by the tenant's own legal reviewer; the system ships none. A clause moves
-- DRAFT -> APPROVED (a recorded legal review) -> PUBLISHED, and is replaced by a new version rather than edited once published.
-- A review is evidence that it took place on the date given, not a guarantee that the clause suits every contract.
CREATE TABLE contract_clauses (
    id                            UUID PRIMARY KEY,
    tenant_id                     UUID NOT NULL,
    family_id                     UUID NOT NULL,
    version_no                    INTEGER NOT NULL DEFAULT 1,
    title                         VARCHAR(200) NOT NULL,
    category                      VARCHAR(40)  NOT NULL,
    body                          TEXT NOT NULL,
    legal_area                    VARCHAR(100),
    jurisdiction                  VARCHAR(100) NOT NULL DEFAULT 'South Africa',
    status                        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    reviewer_name                 VARCHAR(200),
    reviewer_professional_details VARCHAR(300),
    review_date                   DATE,
    next_review_date              DATE,
    review_notes                  TEXT,
    published_at                  TIMESTAMP,
    withdrawn_at                  TIMESTAMP,
    withdrawn_reason              VARCHAR(500),
    created_by                    UUID,
    created_at                    TIMESTAMP NOT NULL DEFAULT now(),
    updated_at                    TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_clause_status CHECK (status IN ('DRAFT', 'APPROVED', 'PUBLISHED', 'SUPERSEDED', 'WITHDRAWN')),
    CONSTRAINT uq_clause_family_version UNIQUE (family_id, version_no)
);
CREATE UNIQUE INDEX uq_clause_one_published_per_family ON contract_clauses (family_id) WHERE status = 'PUBLISHED';
CREATE INDEX idx_contract_clauses_tenant ON contract_clauses (tenant_id, status, category);

-- Which clause versions were put into which contract (written when the change that inserts a clause is applied).
CREATE TABLE contract_clause_usage (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    contract_id       UUID NOT NULL REFERENCES contracts(id),
    clause_id         UUID NOT NULL REFERENCES contract_clauses(id),
    clause_version_no INTEGER NOT NULL,
    proposal_id       UUID,
    contract_version_no INTEGER,
    inserted_by       UUID,
    inserted_at       TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_contract_clause_usage_contract ON contract_clause_usage (tenant_id, contract_id);
CREATE INDEX idx_contract_clause_usage_clause ON contract_clause_usage (tenant_id, clause_id);

ALTER TABLE contract_change_proposals ADD COLUMN IF NOT EXISTS source_clause_id UUID REFERENCES contract_clauses(id);
