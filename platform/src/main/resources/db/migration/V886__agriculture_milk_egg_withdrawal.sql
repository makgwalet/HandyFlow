-- Agriculture: milk and egg withdrawal periods on a health event, next to the existing meat withdrawal period.
-- Applies after V885. Nullable and idempotent.

ALTER TABLE ag_health_events
    ADD COLUMN IF NOT EXISTS milk_withdrawal_days INTEGER,
    ADD COLUMN IF NOT EXISTS egg_withdrawal_days  INTEGER;

-- 0 to 730 days, like the meat period. NOT VALID: new and changed rows are checked, stored rows are not.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_ag_health_events_milk_withdrawal') THEN
        ALTER TABLE ag_health_events ADD CONSTRAINT chk_ag_health_events_milk_withdrawal
            CHECK (milk_withdrawal_days IS NULL OR milk_withdrawal_days BETWEEN 0 AND 730) NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_ag_health_events_egg_withdrawal') THEN
        ALTER TABLE ag_health_events ADD CONSTRAINT chk_ag_health_events_egg_withdrawal
            CHECK (egg_withdrawal_days IS NULL OR egg_withdrawal_days BETWEEN 0 AND 730) NOT VALID;
    END IF;
END $$;
