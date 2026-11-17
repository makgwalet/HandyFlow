-- Tank-gauge feed: a gauge or tank-monitoring system sends a tank's level with its per-company import key. The reading is only staged here.
-- A person accepts it, which records an ordinary dip reading through the existing path (so variance and alerts work as they do today), or rejects it.
CREATE TABLE fuel_gauge_readings (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    key_id         UUID,
    source         VARCHAR(40) NOT NULL,
    external_id    VARCHAR(100) NOT NULL,
    tank_label     VARCHAR(60) NOT NULL,
    litres         NUMERIC(12,2) NOT NULL,
    read_at        TIMESTAMPTZ NOT NULL,
    sender_name    VARCHAR(120),
    payload_hash   VARCHAR(64) NOT NULL,
    problems       TEXT,
    status         VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    received_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_by     UUID,
    decided_at     TIMESTAMPTZ,
    decision_note  VARCHAR(500),
    dip_reading_id UUID,
    CONSTRAINT ck_fuel_gauge_status CHECK (status IN ('PENDING','ACCEPTED','REJECTED')),
    CONSTRAINT ck_fuel_gauge_litres CHECK (litres >= 0)
);
CREATE UNIQUE INDEX uq_fuel_gauge_readings_ref ON fuel_gauge_readings (tenant_id, source, external_id);
CREATE INDEX ix_fuel_gauge_readings_status ON fuel_gauge_readings (tenant_id, status, received_at DESC);

-- What a gauge system calls a tank, per source. Without a label the tank is matched by its name.
CREATE TABLE fuel_import_tank_aliases (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    source      VARCHAR(40) NOT NULL,
    alias       VARCHAR(60) NOT NULL,
    tank_id     UUID NOT NULL REFERENCES fuel_tanks (id),
    created_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_fuel_import_tank_aliases ON fuel_import_tank_aliases (tenant_id, source, alias);
