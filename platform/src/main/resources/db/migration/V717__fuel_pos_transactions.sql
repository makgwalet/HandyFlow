-- Live pump transactions sent by a pump/POS system: sales, and the refunds and voids that reverse them.
-- A transaction belongs to a shift by that shift's externalId (the same source + externalId the finished shift is sent under), so it can arrive
-- before or after the shift itself. Refunds and voids lower the expected takings only when a supervisor accepts the shift. Sales are kept as a
-- record to compare with the meters; they never post stock or money.
CREATE TABLE fuel_pos_transactions (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    key_id               UUID,
    source               VARCHAR(40)  NOT NULL,
    external_id          VARCHAR(100) NOT NULL,
    shift_ref            VARCHAR(100) NOT NULL,
    kind                 VARCHAR(10)  NOT NULL CHECK (kind IN ('SALE','REFUND','VOID')),
    original_external_id VARCHAR(100),
    pump_label           VARCHAR(40),
    litres               NUMERIC(12,3),
    amount               NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    tender               VARCHAR(10),
    occurred_at          TIMESTAMPTZ,
    reason               VARCHAR(300),
    payload_hash         VARCHAR(64)  NOT NULL,
    status               VARCHAR(12)  NOT NULL DEFAULT 'RECEIVED' CHECK (status IN ('RECEIVED','APPLIED','NOT_APPLIED')),
    problems             TEXT,
    shift_id             UUID,
    adjustment_id        UUID,
    received_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_fuel_pos_transactions_ref ON fuel_pos_transactions (tenant_id, source, external_id);
CREATE INDEX ix_fuel_pos_transactions_shift ON fuel_pos_transactions (tenant_id, source, shift_ref);
