-- V54__contract_signing_tokens.sql
--
-- Adds the infrastructure needed for external party signing:
--
-- 1. contract_signing_tokens — one row per party per send, stores the secure
--    token used in the signing URL. Separate from contract_parties so a party
--    can receive multiple tokens (e.g. after a resend) without losing history.
--
-- 2. contract_parties additions — tracks email delivery status and comment
--    capability flags needed for the amendment/comment flow.
--
-- 3. contract_comments — already in the original schema, verified present.

-- ── Signing tokens ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS contract_signing_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    contract_id     UUID NOT NULL REFERENCES contracts(id),
    party_id        UUID NOT NULL REFERENCES contract_parties(id),
    token           VARCHAR(512) NOT NULL UNIQUE,  -- JWT or random hex
    expires_at      TIMESTAMP NOT NULL,
    used_at         TIMESTAMP,                     -- null = not yet used
    revoked_at      TIMESTAMP,                     -- null = still valid; set on resend
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_signing_tokens_token     ON contract_signing_tokens(token) WHERE used_at IS NULL AND revoked_at IS NULL;
CREATE INDEX idx_signing_tokens_party     ON contract_signing_tokens(party_id);

-- ── Add signing_token column to contract_parties for quick lookup ─────────────
-- The active token for a party (denormalised for performance — source of truth is
-- contract_signing_tokens table)
ALTER TABLE contract_parties
    ADD COLUMN IF NOT EXISTS signing_token      VARCHAR(512),
    ADD COLUMN IF NOT EXISTS signing_token_sent_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS email_sent_at      TIMESTAMP,
    ADD COLUMN IF NOT EXISTS declined_at        TIMESTAMP,
    ADD COLUMN IF NOT EXISTS decline_reason     TEXT,
    ADD COLUMN IF NOT EXISTS viewed_at          TIMESTAMP;   -- first time they opened the signing URL

-- ── Contract body hash — for tamper detection ─────────────────────────────────
-- Stores SHA-256 of the body at the time sendForSigning was called.
-- If the body changes after this (which the service now prevents), the hash
-- mismatch is detectable in the audit trail.
ALTER TABLE contracts
    ADD COLUMN IF NOT EXISTS body_hash          VARCHAR(64),  -- SHA-256 hex
    ADD COLUMN IF NOT EXISTS body_locked_at     TIMESTAMP;    -- set when sent for signing

-- ── Contract comments already exist in schema — ensure columns are present ────
-- (The original schema defined contract_comments — this is defensive only)
CREATE TABLE IF NOT EXISTS contract_comments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    contract_id     UUID NOT NULL REFERENCES contracts(id),
    party_id        UUID REFERENCES contract_parties(id),  -- null = internal comment
    comment         TEXT NOT NULL,
    clause_ref      VARCHAR(100),
    is_amendment_request BOOLEAN NOT NULL DEFAULT false,
    resolved        BOOLEAN NOT NULL DEFAULT false,
    resolved_at     TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_contract_comments_contract ON contract_comments(contract_id);
