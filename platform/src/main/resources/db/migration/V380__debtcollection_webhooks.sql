-- RENAME to the next free Flyway version AFTER the Chunk 6 recovery-ledger migration.
-- Same base-column ASSUMPTION as earlier chunks (id, tenant_id, created_at, updated_at, version) — align with AggregateRoot.

CREATE TABLE debtcollection_webhook_endpoints (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ,
    version          BIGINT       NOT NULL DEFAULT 0,
    name             VARCHAR(100) NOT NULL,
    purpose          VARCHAR(20)  NOT NULL,
    token_hash       VARCHAR(64)  NOT NULL,
    secret_version   INT          NOT NULL DEFAULT 1,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by       UUID,
    last_received_at TIMESTAMPTZ,
    revoked_at       TIMESTAMPTZ
);
-- The public receiver looks an endpoint up by token hash alone.
CREATE UNIQUE INDEX uq_dc_webhook_token ON debtcollection_webhook_endpoints (token_hash);
CREATE INDEX ix_dc_webhook_tenant ON debtcollection_webhook_endpoints (tenant_id);

CREATE TABLE debtcollection_webhook_delivery_logs (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ,
    version     BIGINT       NOT NULL DEFAULT 0,
    endpoint_id UUID         NOT NULL REFERENCES debtcollection_webhook_endpoints (id),
    outcome     VARCHAR(25)  NOT NULL,
    http_status INT          NOT NULL,
    detail      VARCHAR(500)
);
CREATE INDEX ix_dc_webhook_log_endpoint ON debtcollection_webhook_delivery_logs (endpoint_id, created_at DESC);

CREATE TABLE debtcollection_webhook_suspense (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID          NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ,
    version            BIGINT        NOT NULL DEFAULT 0,
    kind               VARCHAR(10)   NOT NULL,
    status             VARCHAR(10)   NOT NULL,
    reason             VARCHAR(500)  NOT NULL,
    external_reference VARCHAR(100),
    amount             NUMERIC(15,2),
    currency           VARCHAR(3),
    event_date         DATE,
    channel            VARCHAR(15),
    counterparty       VARCHAR(255),
    body               TEXT,
    payload_json       TEXT,
    payment_source     VARCHAR(15),
    resolved_case_id   UUID,
    resolution_note    VARCHAR(500),
    resolved_by        UUID,
    resolved_at        TIMESTAMPTZ
);
CREATE INDEX ix_dc_suspense_status ON debtcollection_webhook_suspense (tenant_id, status, created_at);
-- Provider retries must not park the same event twice.
CREATE UNIQUE INDEX uq_dc_suspense_reference ON debtcollection_webhook_suspense (tenant_id, kind, external_reference)
    WHERE external_reference IS NOT NULL;
