-- =============================================================================
-- V110__security_guard_auth.sql
-- Guard Authentication Infrastructure
--
-- Adds the token table that makes guard sessions revocable.
-- The guard_registered_device_id column is a placeholder for Phase 2
-- device binding — storing it now avoids a later ALTER TABLE on a busy table.
-- =============================================================================

CREATE TABLE IF NOT EXISTS security_guard_tokens (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id),
    guard_id        UUID        NOT NULL REFERENCES security_guards(id),
    device_id       VARCHAR(200),
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    revoke_reason   VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_guard_tokens_guard
    ON security_guard_tokens(guard_id) WHERE revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_guard_tokens_expires
    ON security_guard_tokens(expires_at) WHERE revoked_at IS NULL;

-- Phase 1.5: guard PIN columns (in case V109 ran before the guard auth code
-- was added — idempotent, safe to re-run)
ALTER TABLE security_guards
    ADD COLUMN IF NOT EXISTS pin_hash                VARCHAR(72),
    ADD COLUMN IF NOT EXISTS pin_set_at              TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS pin_expires_at          TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS pin_must_change         BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS pin_failure_count       INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS pin_locked_until        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS pin_history             TEXT;

-- Registered device ID — used in Phase 2 device binding
ALTER TABLE security_guards
    ADD COLUMN IF NOT EXISTS registered_device_id   VARCHAR(200);

-- Face embedding (Base64 float vector — images are never stored server-side)
ALTER TABLE security_guards
    ADD COLUMN IF NOT EXISTS face_embedding         TEXT;
