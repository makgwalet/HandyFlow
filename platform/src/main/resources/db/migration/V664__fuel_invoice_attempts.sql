-- G02: a durable trail of every hand-off from Fuel to Invoicing, so a failed or half-finished attempt is never silent.
-- Additive only. Nothing here changes how invoices are created.
CREATE TABLE fuel_invoice_attempts (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    customer_id      UUID,
    status           VARCHAR(20) NOT NULL,
    invoice_id       UUID,
    invoice_number   VARCHAR(60),
    error            VARCHAR(500),
    attempted_by     UUID,
    started_at       TIMESTAMPTZ NOT NULL,
    finished_at      TIMESTAMPTZ,
    resolved_by      UUID,
    resolved_at      TIMESTAMPTZ,
    resolution_note  VARCHAR(300),
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_fuel_invoice_attempts_status CHECK (status IN ('STARTED','CREATED','FAILED','LINK_FAILED','RESOLVED'))
);

CREATE TABLE fuel_invoice_attempt_deliveries (
    attempt_id   UUID NOT NULL REFERENCES fuel_invoice_attempts (id) ON DELETE CASCADE,
    delivery_id  UUID NOT NULL,
    PRIMARY KEY (attempt_id, delivery_id)
);

CREATE INDEX idx_fuel_invoice_attempts_tenant_started ON fuel_invoice_attempts (tenant_id, started_at DESC);
CREATE INDEX idx_fuel_invoice_attempts_attention ON fuel_invoice_attempts (tenant_id) WHERE status IN ('STARTED','LINK_FAILED');
