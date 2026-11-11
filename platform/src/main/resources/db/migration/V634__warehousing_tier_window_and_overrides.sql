-- A tenant's Warehousing tier now has a start date, an optional expiry and a note (renewal terms, contract reference). Outside its dates the tier is
-- treated as not assigned: no limits apply. Existing rows have no dates and keep applying exactly as before.
ALTER TABLE whse_tenant_subscription ADD COLUMN IF NOT EXISTS effective_from DATE;
ALTER TABLE whse_tenant_subscription ADD COLUMN IF NOT EXISTS expires_on DATE;
ALTER TABLE whse_tenant_subscription ADD COLUMN IF NOT EXISTS renewal_note VARCHAR(300);
ALTER TABLE whse_tenant_subscription DROP CONSTRAINT IF EXISTS chk_whse_subscription_window;
ALTER TABLE whse_tenant_subscription ADD CONSTRAINT chk_whse_subscription_window CHECK (effective_from IS NULL OR expires_on IS NULL OR expires_on >= effective_from);

-- Platform-administrator overrides: a temporary extra allowance on one limit, with a reason and an end date. The effective limit is the tier's limit plus
-- the overrides in force. Rows are kept for the audit trail; revoking sets revoked_at, nothing is deleted.
CREATE TABLE IF NOT EXISTS whse_tier_overrides (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL,
    limit_key   VARCHAR(30) NOT NULL CHECK (limit_key IN ('WAREHOUSES','CLIENT_ACCOUNTS','MONTHLY_TRANSACTIONS','WEBHOOKS')),
    extra       INTEGER     NOT NULL CHECK (extra BETWEEN 1 AND 100000),
    valid_until DATE        NOT NULL,
    reason      VARCHAR(300) NOT NULL CHECK (length(btrim(reason)) >= 10),
    created_by  UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at  TIMESTAMPTZ,
    revoked_by  UUID
);
CREATE INDEX IF NOT EXISTS idx_whse_tier_overrides_tenant ON whse_tier_overrides (tenant_id, limit_key) WHERE revoked_at IS NULL;
