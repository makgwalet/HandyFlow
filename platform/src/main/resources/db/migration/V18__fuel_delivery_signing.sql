-- Add to V17 or create V18__fuel_delivery_signing.sql

ALTER TABLE fuel_deliveries
    ADD COLUMN IF NOT EXISTS signed_on_behalf  BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS on_behalf_of      VARCHAR(100);