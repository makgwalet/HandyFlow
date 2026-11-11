-- V626: Warehousing events and webhooks, with a transactional outbox.
--
-- A change that others should hear about (stock received, an order shipped or cancelled, picking started) writes an event row in the SAME database
-- transaction as the change, and one pending delivery row per subscribed webhook. A background worker delivers them with retries and backoff, so
-- an event is neither lost if the server stops nor sent for a change that rolled back. Each delivery is signed with the webhook's secret (HMAC-SHA256).
-- After 6 failed attempts a delivery is DEAD (it can be redelivered by hand); after 20 failures in a row the webhook is suspended until resumed.

CREATE TABLE whse_webhooks (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    name                  VARCHAR(120) NOT NULL,
    url                   VARCHAR(500) NOT NULL,
    secret                VARCHAR(100) NOT NULL,
    events                VARCHAR(500) NOT NULL DEFAULT '*',
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    suspended             BOOLEAN NOT NULL DEFAULT FALSE,
    consecutive_failures  INTEGER NOT NULL DEFAULT 0 CHECK (consecutive_failures >= 0),
    created_by            UUID,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at            TIMESTAMPTZ
);
CREATE INDEX idx_whse_webhooks_tenant ON whse_webhooks (tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE whse_outbox_events (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    event_type     VARCHAR(60) NOT NULL,
    aggregate_type VARCHAR(40),
    aggregate_id   UUID,
    payload        JSONB NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_whse_outbox_events_tenant ON whse_outbox_events (tenant_id, created_at DESC);

CREATE TABLE whse_webhook_deliveries (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    webhook_id       UUID NOT NULL REFERENCES whse_webhooks (id),
    event_id         UUID NOT NULL REFERENCES whse_outbox_events (id),
    status           VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RETRY','DELIVERED','DEAD')),
    attempts         INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_status_code INTEGER,
    last_detail      VARCHAR(300),
    last_attempt_at  TIMESTAMPTZ,
    delivered_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_whse_delivery UNIQUE (webhook_id, event_id),
    CONSTRAINT chk_whse_delivery_done CHECK (status <> 'DELIVERED' OR delivered_at IS NOT NULL)
);
CREATE INDEX idx_whse_deliveries_due ON whse_webhook_deliveries (next_attempt_at) WHERE status IN ('PENDING','RETRY');
CREATE INDEX idx_whse_deliveries_tenant ON whse_webhook_deliveries (tenant_id, created_at DESC);
