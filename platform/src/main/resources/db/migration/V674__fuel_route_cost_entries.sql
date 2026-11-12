-- F02: actual costs recorded against a route, to compare with the estimate worked out from the tenant's own cost rates.
-- Entries are never edited or deleted. A wrong one is voided with a reason and a new one is added.
CREATE TABLE fuel_route_cost_entries (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    route_id        UUID NOT NULL REFERENCES fuel_routes(id),
    category        VARCHAR(10) NOT NULL,
    amount          NUMERIC(14,2) NOT NULL,
    quantity        NUMERIC(12,2),
    unit            VARCHAR(10),
    note            VARCHAR(300),
    status          VARCHAR(8) NOT NULL DEFAULT 'ACTIVE',
    void_reason     VARCHAR(300),
    voided_by       UUID,
    voided_at       TIMESTAMPTZ,
    recorded_by     UUID,
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_fuel_route_cost_category CHECK (category IN ('VEHICLE','DRIVER','TOLLS','OTHER')),
    CONSTRAINT chk_fuel_route_cost_amount CHECK (amount >= 0),
    CONSTRAINT chk_fuel_route_cost_quantity CHECK (quantity IS NULL OR quantity >= 0),
    CONSTRAINT chk_fuel_route_cost_status CHECK (status IN ('ACTIVE','VOIDED')),
    CONSTRAINT chk_fuel_route_cost_void CHECK (status = 'ACTIVE' OR (void_reason IS NOT NULL AND voided_at IS NOT NULL))
);
CREATE INDEX idx_fuel_route_cost_route ON fuel_route_cost_entries (tenant_id, route_id);
