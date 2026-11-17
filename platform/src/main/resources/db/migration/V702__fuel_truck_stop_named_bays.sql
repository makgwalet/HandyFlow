-- Named bays for the truck stop. Optional: the bay count in the parking settings still decides capacity, and a stay with no bay assigned works as before.
CREATE TABLE fuel_truck_stop_bays (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    name        VARCHAR(40) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_fuel_truck_stop_bays_name ON fuel_truck_stop_bays (tenant_id, lower(name));

ALTER TABLE fuel_truck_stop_stays ADD COLUMN bay_id UUID REFERENCES fuel_truck_stop_bays (id);
CREATE INDEX ix_fuel_truck_stop_stays_bay ON fuel_truck_stop_stays (tenant_id, bay_id) WHERE bay_id IS NOT NULL;
