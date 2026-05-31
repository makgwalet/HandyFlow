-- V17__fuel_delivery_receipt_fields.sql

-- WHY? Add proof-of-delivery fields needed for mine site receipts.
-- Mine sites require: receiver name, receiver ID/badge, signed timestamp.
-- These are captured when the driver completes the delivery on their device.

ALTER TABLE fuel_deliveries
    ADD COLUMN IF NOT EXISTS receiver_name     VARCHAR(100),
    ADD COLUMN IF NOT EXISTS receiver_id_badge VARCHAR(50),
    ADD COLUMN IF NOT EXISTS receiver_signature_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS meter_reading_start NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS meter_reading_end   NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS receipt_number      VARCHAR(50),
    ADD COLUMN IF NOT EXISTS receipt_generated_at TIMESTAMP;

-- Generate receipt numbers for any existing completed deliveries
-- Format: FDR-{YEAR}-{SEQUENCE}
-- New deliveries get receipt numbers assigned on completion