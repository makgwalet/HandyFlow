-- Counterparty negotiation. A counterparty may propose additions, deletions and replacements of wording and comment on clauses;
-- it can never change the contract itself. Staff accept or reject each proposal, then apply the accepted ones as a new draft
-- revision. A contract with no row here has not started negotiating (NOT_STARTED).
CREATE TABLE contract_negotiations (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    contract_id       UUID NOT NULL REFERENCES contracts(id),
    status            VARCHAR(24) NOT NULL,        -- OPEN, STAFF_REVIEW, CHANGES_REQUIRED, READY_FOR_SIGNATURE, CLOSED
    response_deadline DATE,
    base_version_no   INTEGER NOT NULL,            -- contract version when negotiation opened
    final_version_no  INTEGER,                     -- pinned when marked ready for signature
    final_body_hash   VARCHAR(64),                 -- SHA-256 of that body; sending checks the body still matches
    opened_by         UUID,
    opened_at         TIMESTAMP NOT NULL DEFAULT now(),
    ready_by          UUID,
    ready_at          TIMESTAMP,
    closed_by         UUID,
    closed_at         TIMESTAMP,
    close_reason      VARCHAR(500),
    updated_at        TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_contract_negotiation UNIQUE (contract_id)
);
CREATE INDEX idx_contract_negotiations_tenant ON contract_negotiations (tenant_id, status);

CREATE TABLE contract_negotiation_proposals (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    contract_id      UUID NOT NULL REFERENCES contracts(id),
    negotiation_id   UUID NOT NULL REFERENCES contract_negotiations(id),
    party_id         UUID NOT NULL,                -- the counterparty who proposed it
    kind             VARCHAR(10) NOT NULL,         -- ADD, DELETE, REPLACE
    base_version_no  INTEGER NOT NULL,
    block_index      INTEGER,                      -- position in that version (ADD: the block to insert after, -1 for the start)
    original_html    TEXT,                         -- the block it is attached to (ADD: the block to insert after, null for the start)
    proposed_html    TEXT,                         -- new wording (ADD, REPLACE)
    rationale        VARCHAR(2000),
    status           VARCHAR(16) NOT NULL,         -- PENDING_REVIEW, ACCEPTED, REJECTED, WITHDRAWN, SUPERSEDED, CONFLICTED, APPLIED
    conflict_with    UUID,
    decided_by       UUID,
    decided_at       TIMESTAMP,
    decision_note    VARCHAR(1000),
    applied_version_no INTEGER,
    applied_by       UUID,
    applied_at       TIMESTAMP,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_negotiation_proposals_contract ON contract_negotiation_proposals (tenant_id, contract_id, created_at);

CREATE TABLE contract_negotiation_comments (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    contract_id      UUID NOT NULL REFERENCES contracts(id),
    negotiation_id   UUID NOT NULL REFERENCES contract_negotiations(id),
    author_type      VARCHAR(12) NOT NULL,         -- STAFF, COUNTERPARTY
    author_user_id   UUID,
    party_id         UUID,
    block_index      INTEGER,                      -- clause the comment is about, when it is about one
    block_excerpt    VARCHAR(300),
    proposal_id      UUID,
    parent_id        UUID,
    body             VARCHAR(4000) NOT NULL,
    internal_only    BOOLEAN NOT NULL DEFAULT FALSE,   -- staff notes: never shown to a counterparty
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_negotiation_comments_contract ON contract_negotiation_comments (tenant_id, contract_id, created_at);

CREATE TABLE contract_negotiation_events (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    contract_id      UUID NOT NULL REFERENCES contracts(id),
    negotiation_id   UUID,
    actor_type       VARCHAR(12) NOT NULL,         -- STAFF, COUNTERPARTY, SYSTEM
    actor_user_id    UUID,
    party_id         UUID,
    event_type       VARCHAR(40) NOT NULL,
    detail           VARCHAR(1000),
    ip_address       VARCHAR(64),
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_negotiation_events_contract ON contract_negotiation_events (tenant_id, contract_id, created_at);
