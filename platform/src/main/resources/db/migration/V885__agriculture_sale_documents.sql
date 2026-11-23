-- Agriculture sale documents: the farm's owner and address, and who a sale went to.
-- Applies after V884. All additions are nullable and idempotent.

ALTER TABLE ag_farms
    ADD COLUMN IF NOT EXISTS owner_name       VARCHAR(200),
    ADD COLUMN IF NOT EXISTS physical_address VARCHAR(300);

ALTER TABLE ag_movement_records
    ADD COLUMN IF NOT EXISTS buyer_name           VARCHAR(200),
    ADD COLUMN IF NOT EXISTS buyer_id_number      VARCHAR(60),
    ADD COLUMN IF NOT EXISTS buyer_address        VARCHAR(300),
    ADD COLUMN IF NOT EXISTS destination          VARCHAR(300),
    ADD COLUMN IF NOT EXISTS vehicle_registration VARCHAR(30);
