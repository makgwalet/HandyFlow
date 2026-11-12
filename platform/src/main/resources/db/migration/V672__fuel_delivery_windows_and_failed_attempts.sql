-- C04 (first part): delivery windows and failed delivery attempts. Additive; existing deliveries have no window and no failed attempts.
ALTER TABLE fuel_deliveries
    ADD COLUMN window_start    TIMESTAMPTZ,
    ADD COLUMN window_end      TIMESTAMPTZ,
    ADD COLUMN failed_attempts INTEGER NOT NULL DEFAULT 0;

ALTER TABLE fuel_deliveries
    ADD CONSTRAINT chk_fuel_deliveries_window CHECK (window_start IS NULL OR window_end IS NULL OR window_end > window_start),
    ADD CONSTRAINT chk_fuel_deliveries_failed_attempts CHECK (failed_attempts >= 0);

-- One row per failed attempt. Append-only: never updated or deleted.
CREATE TABLE fuel_delivery_exceptions (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    delivery_id       UUID NOT NULL REFERENCES fuel_deliveries(id),
    kind              VARCHAR(16) NOT NULL DEFAULT 'FAILED_ATTEMPT',
    reason_code       VARCHAR(30) NOT NULL,
    note              VARCHAR(500),
    litres_returned   NUMERIC(12,2) NOT NULL DEFAULT 0,
    rescheduled_to    TIMESTAMPTZ,
    attempt_number    INTEGER NOT NULL,
    recorded_by       UUID,
    recorded_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    client_request_id UUID,
    CONSTRAINT chk_fuel_delivery_exceptions_kind CHECK (kind IN ('FAILED_ATTEMPT')),
    CONSTRAINT chk_fuel_delivery_exceptions_litres CHECK (litres_returned >= 0)
);
CREATE INDEX idx_fuel_delivery_exceptions_delivery ON fuel_delivery_exceptions (tenant_id, delivery_id, recorded_at);
CREATE UNIQUE INDEX uq_fuel_delivery_exceptions_request ON fuel_delivery_exceptions (tenant_id, delivery_id, client_request_id) WHERE client_request_id IS NOT NULL;
