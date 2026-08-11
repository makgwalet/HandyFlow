-- Rename this file to the actual next Flyway version before running.

ALTER TABLE customers
    ADD COLUMN pipeline_stage VARCHAR(20);

-- Backfill existing LEAD-type customers to NEW, matching what a freshly
-- created lead now gets by default (Customer.create()) — otherwise every
-- lead that existed before this migration would show no stage at all.
UPDATE customers SET pipeline_stage = 'NEW' WHERE customer_type = 'LEAD' AND deleted_at IS NULL;