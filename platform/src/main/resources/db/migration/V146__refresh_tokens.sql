-- V___refresh_tokens.sql
-- (rename to the next available Flyway version number in your sequence)
--
-- Backs the refresh-token architecture. Previously: a single 24-hour
-- access token with no refresh mechanism and no revocation path at all —
-- a stolen token was valid for a full day with nothing anyone could do
-- about it. This table lets access tokens shrink to a short lifetime
-- (config change, separate from this migration) while sessions stay
-- alive via rotation, and gives real revocation: "sign out everywhere",
-- and automatic full revocation if a rotated-away token is ever reused
-- (a strong signal of theft — see RefreshTokenService for the detection
-- logic).

CREATE TABLE refresh_tokens (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id                 UUID        NOT NULL,
    tenant_id               UUID        NOT NULL,

    -- Never store the raw token — only a SHA-256 hash of it. Losing this
    -- table to a read-only SQL injection or a DB backup leak should not
    -- itself be enough to forge a session.
    token_hash              VARCHAR(64) NOT NULL,

    -- Device binding — not perfect, but raises the bar. Captured at issue
    -- time from user-agent + a client-computed fingerprint.
    device_fingerprint      VARCHAR(255),
    ip_address              VARCHAR(64),
    user_agent              TEXT,

    created_at              TIMESTAMP   NOT NULL DEFAULT NOW(),
    last_used_at            TIMESTAMP,
    expires_at              TIMESTAMP   NOT NULL,

    -- NULL = still active. Set the moment a token is rotated away OR
    -- explicitly revoked (logout, "sign out everywhere", theft detection).
    revoked_at               TIMESTAMP,

    -- Rotation chain — lets a reuse-detection event ("someone presented an
    -- already-revoked token") be traced forward to know exactly which
    -- later token superseded it, useful for incident investigation even
    -- though the detection response itself (revoke everything for this
    -- user) doesn't need this value to act.
    replaced_by_token_hash  VARCHAR(64),

    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);

-- The hot path on every refresh call: look up by hash.
CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);
-- Powers "sign out everywhere" and the theft-detection sweep (revoke
-- every other active token for this user in one statement).
CREATE INDEX idx_refresh_tokens_user_active ON refresh_tokens(user_id)
    WHERE revoked_at IS NULL;