-- Phase 8c (G01): optional limits on what a Fuel user can reach, by division and by depot.
-- A user with NO rows here is unrestricted, exactly as before, so every existing tenant keeps its access.
-- Division rows limit which divisions the user can use; depot rows limit which depots (and their tanks) the user can reach.
CREATE TABLE fuel_user_scopes (
    id         UUID PRIMARY KEY,
    tenant_id  UUID NOT NULL,
    user_id    UUID NOT NULL,
    division   VARCHAR(30),
    depot_id   UUID REFERENCES fuel_depots(id),
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_fuel_user_scopes_one CHECK ((division IS NOT NULL) <> (depot_id IS NOT NULL)),
    CONSTRAINT ck_fuel_user_scopes_division CHECK (division IS NULL OR division IN ('WHOLESALE_SUPPLY','DISTRIBUTION','RETAIL_FORECOURT','TRUCK_STOP'))
);
CREATE UNIQUE INDEX uq_fuel_user_scopes_division ON fuel_user_scopes (tenant_id, user_id, division) WHERE division IS NOT NULL;
CREATE UNIQUE INDEX uq_fuel_user_scopes_depot ON fuel_user_scopes (tenant_id, user_id, depot_id) WHERE depot_id IS NOT NULL;
CREATE INDEX idx_fuel_user_scopes_user ON fuel_user_scopes (tenant_id, user_id);
