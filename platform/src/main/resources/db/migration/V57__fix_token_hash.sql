-- V56__fix_token_hash.sql
--
-- Backfill token_hash for any existing rows that have a null token_hash.
-- The ContractSigningToken entity was updated to always populate token_hash
-- going forward (SHA-256 of the raw token), but existing rows from before
-- the fix will have null values that prevent future saves due to the NOT NULL
-- constraint being evaluated at table scan time in some Postgres versions.
--
-- This migration is safe to run multiple times (WHERE token_hash IS NULL guard).
-- It computes SHA-256 using pgcrypto — ensure the extension is available.
--
-- APPLY: add this file to src/main/resources/db/migration/ and restart.

-- Enable pgcrypto if not already enabled (safe if already exists)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Backfill: set token_hash = SHA-256 hex of the raw token for any null rows
UPDATE contract_signing_tokens
SET    token_hash = encode(digest(token, 'sha256'), 'hex')
WHERE  token_hash IS NULL;

-- If the column was added without a NOT NULL constraint and you need to enforce it now:
-- ALTER TABLE contract_signing_tokens ALTER COLUMN token_hash SET NOT NULL;
-- (Skip this if the constraint already exists — Flyway will fail gracefully)
