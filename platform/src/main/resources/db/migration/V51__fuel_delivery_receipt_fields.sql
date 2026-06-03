-- V51__fuel_delivery_receipt_fields.sql
-- Adds receiver details, meter readings, receipt tracking, and on-behalf-of
-- to fuel_deliveries. These fields are referenced by FuelDelivery.complete()
-- and DeliveryReceiptPdfService but were absent from V15.

ALTER TABLE fuel_deliveries
    ADD COLUMN IF NOT EXISTS receiver_name       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS receiver_id_badge   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS meter_reading_start NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS meter_reading_end   NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS receipt_number      VARCHAR(30),
    ADD COLUMN IF NOT EXISTS receipt_generated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS signed_on_behalf    BOOLEAN  NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS on_behalf_of        VARCHAR(255);

-- Unique index so duplicate receipt numbers are caught at DB level
CREATE UNIQUE INDEX IF NOT EXISTS uq_fuel_delivery_receipt_number
    ON fuel_deliveries (tenant_id, receipt_number)
    WHERE receipt_number IS NOT NULL;

-- Index to speed up receipt number generation query (COUNT by year)
CREATE INDEX IF NOT EXISTS idx_fuel_deliveries_receipt_year
    ON fuel_deliveries (receipt_generated_at)
    WHERE receipt_number IS NOT NULL;
