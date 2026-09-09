-- V271__fuel_cost_margin_engine.sql
-- Fuel cost/margin engine, per the design proposal agreed with the
-- product owner: weighted-average cost (WAC), snapshotted at time of
-- sale onto each delivery/dispatch. Fuel in a shared tank is fungible --
-- WAC is the standard approach for exactly this situation (FIFO doesn't
-- make physical sense for liquid mixed in one tank).
--
-- costPerLitreWac lives on the tank (nullable -- a brand-new tank with
-- no receipts yet has no WAC). costPerLitreAtSale is captured on EVERY
-- delivery and dispatch, including internal (non-customer-billed) ones
-- per the product owner's explicit decision -- this doubles as an
-- internal fleet-fuel-cost tracker alongside external margin reporting,
-- both views over the same underlying snapshot.
--
-- Deliberately NOT backfilling existing rows (also an explicit product
-- decision) -- historical deliveries/dispatches show no margin data;
-- only new transactions going forward get it.

ALTER TABLE fuel_tanks
    ADD COLUMN cost_per_litre_wac NUMERIC(10,4);

ALTER TABLE fuel_deliveries
    ADD COLUMN cost_per_litre_at_sale NUMERIC(10,4);

ALTER TABLE fuel_dispatches
    ADD COLUMN cost_per_litre_at_sale NUMERIC(10,4);

COMMENT ON COLUMN fuel_tanks.cost_per_litre_wac IS
    'Running weighted-average cost per litre, recalculated on every FuelReceipt: (currentLitres * currentWac + receivedLitres * receiptPrice) / (currentLitres + receivedLitres). Null until the tank''s first receipt.';

COMMENT ON COLUMN fuel_deliveries.cost_per_litre_at_sale IS
    'Snapshot of the tank''s cost_per_litre_wac at the moment this delivery was completed -- NOT a live reference. Margin = (pricePerLitre - costPerLitreAtSale) * litresDelivered. Null for deliveries recorded before this feature existed (not backfilled) or if the tank had no WAC yet (no receipts recorded before this delivery).';

COMMENT ON COLUMN fuel_dispatches.cost_per_litre_at_sale IS
    'Snapshot of the tank''s cost_per_litre_wac at the moment this dispatch was recorded. Captured for EVERY dispatch, not just customer-billed ones (pricePerLitre set) -- internal fleet/asset dispatches get this too, giving an internal fuel-cost view even where there is no sale/margin. Null for dispatches recorded before this feature existed (not backfilled) or if the tank had no WAC yet.';

-- New permission gating the margin report specifically -- margin reveals
-- wholesale cost, which is more sensitive than transaction data itself
-- (a dispatcher needs to see a delivery happened; they don't necessarily
-- need to see what the fuel in it cost). Separate from FUEL_READ/
-- FUEL_MANAGE per the product owner's explicit decision. Same seeding
-- pattern as V269 -- auto-granted to every tenant's ADMIN role, matching
-- V220's established precedent for this class of migration.
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'FUEL_MARGIN_READ', 'View fuel cost and margin reports')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name = 'FUEL_MARGIN_READ'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
