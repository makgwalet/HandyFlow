-- Phase 7a: fuel that came back on the truck and was put into a tank. Append-only record of where it went.
-- It books NO stock movement: a tank is only deducted when a delivery completes, so litres that never left the book
-- are already in the tank's book balance. This record confirms where they physically went, so they are not lost track of.
CREATE TABLE fuel_load_returns (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    delivery_id       UUID NOT NULL REFERENCES fuel_deliveries(id),
    tank_id           UUID NOT NULL REFERENCES fuel_tanks(id),
    litres            NUMERIC(12,2) NOT NULL,
    note              VARCHAR(500),
    recorded_by       UUID,
    recorded_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    client_request_id UUID,
    CONSTRAINT ck_fuel_load_returns_litres CHECK (litres > 0)
);
CREATE INDEX idx_fuel_load_returns_delivery ON fuel_load_returns (tenant_id, delivery_id, recorded_at);
CREATE INDEX idx_fuel_load_returns_tank ON fuel_load_returns (tenant_id, tank_id);
CREATE UNIQUE INDEX uq_fuel_load_returns_request ON fuel_load_returns (tenant_id, delivery_id, client_request_id) WHERE client_request_id IS NOT NULL;
