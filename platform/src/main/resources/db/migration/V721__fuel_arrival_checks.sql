-- N3: the driver's arrival eligibility check at the site, with its result and time (read-only: it changes nothing on the load).
CREATE TABLE fuel_arrival_checks (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    delivery_id UUID NOT NULL,
    checked_by UUID,
    allowed BOOLEAN NOT NULL,
    findings TEXT NOT NULL,
    checked_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_fuel_arrival_checks_delivery ON fuel_arrival_checks (tenant_id, delivery_id, checked_at DESC);
