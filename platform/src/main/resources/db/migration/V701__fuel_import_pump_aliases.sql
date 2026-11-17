-- A pump or POS system may call a pump by its own id or label. An alias maps that label, for one source, to a Fuel pump.
-- Without an alias the import still matches by pump name, so existing senders are unchanged.
CREATE TABLE fuel_import_pump_aliases (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    source      VARCHAR(40) NOT NULL,
    alias       VARCHAR(40) NOT NULL,
    pump_id     UUID NOT NULL REFERENCES fuel_pumps (id),
    created_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_fuel_import_pump_aliases ON fuel_import_pump_aliases (tenant_id, source, alias);
