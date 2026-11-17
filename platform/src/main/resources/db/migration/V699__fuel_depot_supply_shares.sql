-- A forecourt may be supplied by several depots. Each row says what percentage of the forecourt's supply is attributed to one supplying depot.
-- Optional: a depot with no rows keeps using fuel_depots.supplying_depot_id (single supplier), so existing companies see no change.
-- The percentages of one depot must total 100; the service enforces that when the set is saved.
CREATE TABLE fuel_depot_supply_shares (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    depot_id            UUID NOT NULL REFERENCES fuel_depots (id),
    supplying_depot_id  UUID NOT NULL REFERENCES fuel_depots (id),
    percentage          NUMERIC(5,2) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_fuel_supply_share_pct CHECK (percentage > 0 AND percentage <= 100),
    CONSTRAINT ck_fuel_supply_share_not_self CHECK (depot_id <> supplying_depot_id)
);
CREATE UNIQUE INDEX uq_fuel_depot_supply_shares ON fuel_depot_supply_shares (tenant_id, depot_id, supplying_depot_id);
