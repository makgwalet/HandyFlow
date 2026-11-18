-- Counterparty access to a negotiation. A negotiation link is its own credential, never a signing link. Only the SHA-256 of the
-- link token is stored, so a database leak cannot yield a working link. Opening a link is not enough: the counterparty confirms a
-- one-time code (kept in contract_otp_verifications, keyed by the link id) and receives a short-lived session, also stored hashed.
CREATE TABLE contract_negotiation_links (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    contract_id      UUID NOT NULL REFERENCES contracts(id),
    negotiation_id   UUID NOT NULL REFERENCES contract_negotiations(id),
    party_id         UUID NOT NULL,
    token_hash       VARCHAR(64) NOT NULL,
    expires_at       TIMESTAMP NOT NULL,
    revoked_at       TIMESTAMP,
    revoked_reason   VARCHAR(200),
    issued_by        UUID,
    delivery_status  VARCHAR(20),                  -- QUEUED, NO_CHANNEL
    last_accessed_at TIMESTAMP,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_contract_negotiation_link_hash UNIQUE (token_hash)
);
CREATE INDEX idx_contract_negotiation_links_contract ON contract_negotiation_links (tenant_id, contract_id, party_id);

CREATE TABLE contract_negotiation_sessions (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    link_id       UUID NOT NULL REFERENCES contract_negotiation_links(id) ON DELETE CASCADE,
    session_hash  VARCHAR(64) NOT NULL,
    expires_at    TIMESTAMP NOT NULL,
    ip_address    VARCHAR(64),
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_contract_negotiation_session_hash UNIQUE (session_hash)
);
CREATE INDEX idx_contract_negotiation_sessions_expiry ON contract_negotiation_sessions (expires_at);
