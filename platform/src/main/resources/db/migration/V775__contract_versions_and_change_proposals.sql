-- Every change to a contract's body is kept as a numbered version, and body changes are made by proposing a revised text that a
-- reviewer accepts or rejects block by block. Only contracts that are still editable (DRAFT, UNDER_REVIEW) can change.
CREATE TABLE contract_versions (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    contract_id  UUID NOT NULL REFERENCES contracts(id),
    version_no   INTEGER NOT NULL,
    body         TEXT NOT NULL,
    source       VARCHAR(30) NOT NULL,          -- BASELINE, CREATED, EDITED, PROPOSAL_APPLIED
    note         VARCHAR(500),
    created_by   UUID,
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_contract_version UNIQUE (contract_id, version_no)
);
CREATE INDEX idx_contract_versions_contract ON contract_versions (tenant_id, contract_id, version_no DESC);

CREATE TABLE contract_change_proposals (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    contract_id       UUID NOT NULL REFERENCES contracts(id),
    base_version_no   INTEGER NOT NULL,
    proposed_body     TEXT NOT NULL,
    note              VARCHAR(1000),
    status            VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    proposed_by       UUID,
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    decided_by        UUID,
    decided_at        TIMESTAMP,
    decision_note     VARCHAR(1000),
    accepted_hunks    VARCHAR(500),
    applied_version_no INTEGER,
    CONSTRAINT chk_change_proposal_status CHECK (status IN ('OPEN', 'APPLIED', 'REJECTED'))
);
CREATE INDEX idx_contract_change_proposals_contract ON contract_change_proposals (tenant_id, contract_id, created_at DESC);
