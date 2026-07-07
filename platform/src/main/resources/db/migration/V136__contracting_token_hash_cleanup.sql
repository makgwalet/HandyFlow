-- V___contracting_token_hash_cleanup.sql
-- (rename to the next available Flyway version number in your sequence)
--
-- Completes the fix V56/V57 started but never finished — see
-- ContractSigningTokenRepository.java and ContractingService.java for the
-- corresponding code changes (findByToken -> findByTokenHash).

-- 1. Confirm token_hash is genuinely fully populated before trying to
--    enforce NOT NULL on it. If this SELECT returns any rows, STOP — do not
--    proceed with this migration until you understand why V56/V57's backfill
--    didn't reach every row.
--
--    SELECT id, contract_id, party_id FROM contract_signing_tokens WHERE token_hash IS NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM contract_signing_tokens WHERE token_hash IS NULL) THEN
        RAISE EXCEPTION 'contract_signing_tokens has rows with a NULL token_hash — '
            'run the diagnostic query in this migration''s header comment before proceeding.';
    END IF;
END $$;

-- Now safe to actually enforce it — V56 set this, but V57's copy of the same
-- statement was deliberately commented out "to be safe", leaving it genuinely
-- unclear whether the constraint survived in every environment.
ALTER TABLE contract_signing_tokens
    ALTER COLUMN token_hash SET NOT NULL;

-- 2. The old lookup-by-raw-token index is now dead weight — nothing queries
--    by the raw `token` column anymore (see findValidToken()'s fix). The
--    token column itself stays: it's still legitimately needed to embed the
--    actual value in outgoing signing-link URLs, just never used as an
--    authentication query key anymore.
DROP INDEX IF EXISTS idx_signing_tokens_token;

-- 3. contract_parties.signing_token was a second, unsynchronized raw copy of
--    the same secret ContractSigningToken already stores properly (alongside
--    its hash, expiry, and revocation tracking). All code reading/writing it
--    has been removed — see ContractParty.java and
--    ContractingService.notifyNextParty(). Clear any residual raw tokens
--    still at rest before dropping the column, so nothing sensitive lingers
--    in a soon-to-be-deleted column any longer than necessary.
UPDATE contract_parties SET signing_token = NULL WHERE signing_token IS NOT NULL;

ALTER TABLE contract_parties DROP COLUMN IF EXISTS signing_token;
