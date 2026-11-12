-- Phase 8b: optional map position of a customer site, used only to suggest a stop order for a route.
-- Both or neither. Existing sites keep NULL, so nothing changes for them.
ALTER TABLE fuel_customer_sites ADD COLUMN IF NOT EXISTS latitude  NUMERIC(9,6);
ALTER TABLE fuel_customer_sites ADD COLUMN IF NOT EXISTS longitude NUMERIC(9,6);
ALTER TABLE fuel_customer_sites ADD CONSTRAINT chk_fuel_site_coordinates CHECK (
    (latitude IS NULL AND longitude IS NULL)
    OR (latitude IS NOT NULL AND longitude IS NOT NULL AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180));
