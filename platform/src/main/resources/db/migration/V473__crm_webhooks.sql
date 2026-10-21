-- V473: outgoing webhooks. When something happens in the CRM, HandyFlow sends a signed message to the address the account chose.
CREATE TABLE crm_webhooks (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID         NOT NULL,
    name                 VARCHAR(120) NOT NULL,
    url                  VARCHAR(500) NOT NULL,
    secret               VARCHAR(80)  NOT NULL,           -- signs each message; kept in full because it must be used to sign
    events               VARCHAR(200) NOT NULL,           -- comma-separated event names
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    consecutive_failures INTEGER      NOT NULL DEFAULT 0,
    suspended_at         TIMESTAMPTZ,                     -- set after too many failures in a row; saving the webhook again clears it
    last_delivery_at     TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at           TIMESTAMPTZ
);
CREATE INDEX idx_crm_webhooks_tenant ON crm_webhooks (tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE crm_webhook_deliveries (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    webhook_id  UUID         NOT NULL REFERENCES crm_webhooks (id),
    event       VARCHAR(30)  NOT NULL,
    outcome     VARCHAR(10)  NOT NULL,                    -- DONE or FAILED
    status_code INTEGER,
    detail      VARCHAR(300),
    sent_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_crm_webhook_deliveries_hook ON crm_webhook_deliveries (webhook_id, sent_at DESC);
CREATE INDEX idx_crm_webhook_deliveries_tenant ON crm_webhook_deliveries (tenant_id, sent_at DESC);
