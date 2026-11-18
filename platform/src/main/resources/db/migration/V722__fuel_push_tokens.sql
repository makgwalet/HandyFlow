-- Expo push tokens for the Fuel app. One live token per phone; a token moves to whoever signs in on that phone last,
-- and a token the push service reports as dead is revoked, never deleted, so there is a record of what was sent where.
CREATE TABLE IF NOT EXISTS fuel_push_tokens (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenants(id),
    user_id     UUID         NOT NULL,
    device_id   VARCHAR(100) NOT NULL,
    expo_token  VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    revoked_at  TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_fuel_push_token ON fuel_push_tokens(tenant_id, expo_token);
CREATE INDEX IF NOT EXISTS idx_fuel_push_user ON fuel_push_tokens(tenant_id, user_id) WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_fuel_push_device ON fuel_push_tokens(tenant_id, device_id) WHERE revoked_at IS NULL;
