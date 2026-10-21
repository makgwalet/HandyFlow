-- V472: keys that let a website form or another system send leads into the CRM without a login. Only the hash of a key is stored.
CREATE TABLE crm_capture_keys (
    id             UUID PRIMARY KEY,
    tenant_id      UUID         NOT NULL,
    name           VARCHAR(120) NOT NULL,
    key_hash       VARCHAR(64)  NOT NULL UNIQUE,
    key_prefix     VARCHAR(16)  NOT NULL,
    default_source VARCHAR(20)  NOT NULL DEFAULT 'WEBSITE',   -- the lead source used when the sender does not give a valid one
    created_by     UUID,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_used_at   TIMESTAMPTZ,
    revoked_at     TIMESTAMPTZ
);
CREATE INDEX idx_crm_capture_keys_tenant ON crm_capture_keys (tenant_id);
