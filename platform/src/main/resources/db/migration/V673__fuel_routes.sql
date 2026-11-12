-- C04 part 2: a route is one tanker and one driver carrying several loads (stops) in an order.
-- A stop is an existing delivery that points at the route; nothing about a delivery's own status or money changes.
CREATE TABLE fuel_routes (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    route_date          DATE NOT NULL,
    label               VARCHAR(100),
    tanker_id           UUID NOT NULL REFERENCES fuel_tankers(id),
    driver_employee_id  UUID NOT NULL,
    status              VARCHAR(12) NOT NULL DEFAULT 'PLANNED',
    cancel_reason       VARCHAR(300),
    created_by          UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    cancelled_at        TIMESTAMPTZ,
    CONSTRAINT chk_fuel_routes_status CHECK (status IN ('PLANNED','CANCELLED'))
);
CREATE INDEX idx_fuel_routes_tenant_date ON fuel_routes (tenant_id, route_date);

ALTER TABLE fuel_deliveries
    ADD COLUMN route_id       UUID REFERENCES fuel_routes(id),
    ADD COLUMN route_sequence INTEGER;

ALTER TABLE fuel_deliveries
    ADD CONSTRAINT chk_fuel_deliveries_route_seq CHECK ((route_id IS NULL AND route_sequence IS NULL) OR (route_id IS NOT NULL AND route_sequence IS NOT NULL AND route_sequence >= 1));

-- One stop per position on a route. Deferred so a reorder can swap positions inside one transaction.
ALTER TABLE fuel_deliveries
    ADD CONSTRAINT uq_fuel_deliveries_route_position UNIQUE (route_id, route_sequence) DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX idx_fuel_deliveries_route ON fuel_deliveries (tenant_id, route_id) WHERE route_id IS NOT NULL;
