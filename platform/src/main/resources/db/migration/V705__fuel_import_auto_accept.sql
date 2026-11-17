-- Optional automatic acceptance of imported pump/POS shifts and tank-gauge readings. No row, or both switches off, means every import waits for a person (today's behaviour).
CREATE TABLE fuel_import_auto_accept (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL UNIQUE,
    shifts_enabled              BOOLEAN NOT NULL DEFAULT FALSE,
    gauges_enabled              BOOLEAN NOT NULL DEFAULT FALSE,
    gauge_max_difference_litres NUMERIC(14,2),
    updated_by                  UUID,
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
