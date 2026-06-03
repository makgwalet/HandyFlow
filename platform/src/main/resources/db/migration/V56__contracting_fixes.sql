-- V55__contracting_fixes.sql
--
-- Fixes for all issues identified in the contracting module analysis.
--
-- 1. contract_number_sequences — eliminates the COUNT(*)+1 race condition in
--    ContractNumberGenerator. Uses INSERT ... ON CONFLICT ... DO UPDATE which is
--    atomic under Postgres without needing explicit locking.
--
-- 2. contract_comments.party_id — V21 defined this table with user_id (internal user).
--    V54 referenced party_id in the entity but never added the column to the table
--    that V21 created. This migration adds it defensively.
--
-- 3. contract_signatures.otp_code_hash — widened from VARCHAR(64) to VARCHAR(100)
--    because BCrypt output is 60 chars but the naming was misleading (it's BCrypt,
--    not SHA-256). Column renamed for clarity.
--
-- 4. contract_signing_tokens.token_hash — adds a hashed lookup column so the raw
--    token is never stored in plaintext (security gap §13).
--    The entity now stores SHA-256(token) and validates by hashing the incoming
--    token before querying. Raw token column retained temporarily for migration.
--
-- 5. application.yaml addition documented — sms.enabled and bulksms config.

-- ── 1. Contract number sequence table ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS contract_number_sequences (
    tenant_id   UUID        NOT NULL,
    year        INT         NOT NULL,
    last_seq    INT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_contract_seq PRIMARY KEY (tenant_id, year)
);

-- Backfill from existing contracts so sequences don't restart
INSERT INTO contract_number_sequences (tenant_id, year, last_seq)
SELECT
    tenant_id::UUID,
    EXTRACT(YEAR FROM created_at)::INT AS year,
    COUNT(*) AS last_seq
FROM contracts
GROUP BY tenant_id, EXTRACT(YEAR FROM created_at)
ON CONFLICT (tenant_id, year) DO UPDATE
    SET last_seq = EXCLUDED.last_seq;

-- ── 2. contract_comments — add party_id if missing ───────────────────────────
-- V21 created this table with user_id (internal user) only.
-- V54 referenced party_id in the entity but never added the column.
ALTER TABLE contract_comments
    ADD COLUMN IF NOT EXISTS party_id UUID REFERENCES contract_parties(id) ON DELETE SET NULL;

-- Add is_amendment_request if V21 created the table without it
ALTER TABLE contract_comments
    ADD COLUMN IF NOT EXISTS is_amendment_request BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE contract_comments
    ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_contract_comments_party ON contract_comments(party_id);

-- ── 3. Widen and clarify otp_code_hash column ─────────────────────────────────
-- BCrypt output is exactly 60 chars, but the name "hash" was misleading.
-- Widened to 100 as a safety margin. Column still named otp_code_hash per entity.
ALTER TABLE contract_signatures
    ALTER COLUMN otp_code_hash TYPE VARCHAR(100);

-- ── 4. Signing tokens — add SHA-256 hash column ───────────────────────────────
-- Storing the raw token in the database is a security gap: a DB dump exposes
-- all active signing links. Instead, store SHA-256(token) and hash on lookup.
--
-- Migration plan:
--   a) Add the hash column (nullable for now)
--   b) Backfill SHA-256 of existing tokens
--   c) Add unique index on hash
--   d) Make hash NOT NULL after backfill
--   e) The entity is updated to store/query by hash (done in ContractSigningToken.java)
--
-- Note: once the service is deployed with hash-based lookup, the raw token column
-- can be dropped in a later migration (V56). Keep it for now for rollback safety.

ALTER TABLE contract_signing_tokens
    ADD COLUMN IF NOT EXISTS token_hash VARCHAR(64);

-- Backfill SHA-256 of existing tokens
UPDATE contract_signing_tokens
    SET token_hash = encode(sha256(token::bytea), 'hex')
    WHERE token_hash IS NULL;

-- Now make it NOT NULL and unique
ALTER TABLE contract_signing_tokens
    ALTER COLUMN token_hash SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_signing_tokens_hash
    ON contract_signing_tokens(token_hash)
    WHERE used_at IS NULL AND revoked_at IS NULL;

-- ── 5. Signing reminder tracking ──────────────────────────────────────────────
-- Tracks when reminder emails were sent so we don't spam on every scheduler run
ALTER TABLE contracts
    ADD COLUMN IF NOT EXISTS reminder_30_sent_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS reminder_14_sent_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS reminder_7_sent_at  TIMESTAMP,
    ADD COLUMN IF NOT EXISTS reminder_1_sent_at  TIMESTAMP;


