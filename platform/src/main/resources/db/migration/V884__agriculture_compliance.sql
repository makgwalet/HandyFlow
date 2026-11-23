-- Agriculture compliance: the farm's registered marks, and a pre-harvest interval on each crop input application.
-- Applies after V883. All additions are nullable and idempotent.

ALTER TABLE ag_farms
    ADD COLUMN IF NOT EXISTS brand_mark               VARCHAR(60),
    ADD COLUMN IF NOT EXISTS ear_mark                 VARCHAR(60),
    ADD COLUMN IF NOT EXISTS mark_registration_number VARCHAR(60);

ALTER TABLE ag_input_applications
    ADD COLUMN IF NOT EXISTS pre_harvest_interval_days INTEGER;

-- Periods are 0 to 730 days. NOT VALID: new and changed rows are checked, rows already stored are not,
-- so a migration never fails on old data (VALIDATE CONSTRAINT after cleaning up, if wanted).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_ag_input_applications_phi') THEN
        ALTER TABLE ag_input_applications ADD CONSTRAINT chk_ag_input_applications_phi
            CHECK (pre_harvest_interval_days IS NULL OR pre_harvest_interval_days BETWEEN 0 AND 730) NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_ag_health_events_withdrawal') THEN
        ALTER TABLE ag_health_events ADD CONSTRAINT chk_ag_health_events_withdrawal
            CHECK (withdrawal_period_days IS NULL OR withdrawal_period_days BETWEEN 0 AND 730) NOT VALID;
    END IF;
END $$;
