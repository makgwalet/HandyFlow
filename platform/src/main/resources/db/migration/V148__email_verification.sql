-- V___email_verification.sql
-- (rename to the next available Flyway version number in your sequence)
--
-- NEW feature: previously no email verification existed at all. Kept
-- deliberately non-blocking — verifying an account tracks and nudges,
-- it doesn't gate login or app usage. Matches this codebase's existing
-- philosophy elsewhere (CIPC registration number is explicitly "optional
-- at registration, nudged post-onboarding" per the original registration
-- analysis) rather than introducing a hard block that would be
-- inconsistent with how every other piece of registration friction in
-- this platform has been deliberately deferred, not enforced upfront.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_verified    BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMP;

CREATE TABLE email_verification_tokens (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL,
    tenant_id   UUID        NOT NULL,
    token       VARCHAR(128) NOT NULL,
    expires_at  TIMESTAMP   NOT NULL,
    used        BOOLEAN     NOT NULL DEFAULT false,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_email_verification_tokens PRIMARY KEY (id),
    CONSTRAINT uq_email_verification_tokens_token UNIQUE (token)
);

CREATE INDEX idx_email_verification_tokens_user ON email_verification_tokens(user_id);
