-- Adds a public, single-purpose access token for the client-facing
-- accept/reject link. Deliberately NOT the quote's own id — see design
-- notes: a separate token can be rotated/revoked without touching the
-- quote's real identity, and doesn't leak anything if forwarded/cached.
ALTER TABLE quotes
    ADD COLUMN public_access_token UUID;

CREATE UNIQUE INDEX idx_quotes_public_access_token
    ON quotes (public_access_token)
    WHERE public_access_token IS NOT NULL;

-- Backfill existing rows so nothing is left without a token.
UPDATE quotes SET public_access_token = gen_random_uuid()
WHERE public_access_token IS NULL;