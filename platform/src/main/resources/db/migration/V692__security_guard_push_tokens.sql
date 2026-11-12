-- Expo push tokens for the Guard app. One live token per phone; a token moves to whoever signs in on that phone last,
-- and a token the push service reports as dead is revoked, never deleted, so there is a record of what was sent where.
CREATE TABLE IF NOT EXISTS security_guard_push_tokens (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL REFERENCES tenants(id),
    guard_id           UUID         NOT NULL REFERENCES security_guards(id),
    device_hardware_id VARCHAR(100) NOT NULL,
    expo_token         VARCHAR(255) NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    revoked_at         TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_guard_push_token ON security_guard_push_tokens(tenant_id, expo_token);
CREATE INDEX IF NOT EXISTS idx_guard_push_guard ON security_guard_push_tokens(tenant_id, guard_id) WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_guard_push_device ON security_guard_push_tokens(tenant_id, device_hardware_id) WHERE revoked_at IS NULL;
