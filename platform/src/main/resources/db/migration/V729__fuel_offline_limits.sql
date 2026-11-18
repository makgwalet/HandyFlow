-- How old a queued offline write may be when it reaches the server, and how far ahead a phone's clock may be.
-- Were constants in Batch F (72 h, 5 min). Additive; a company with no change keeps the same behaviour.
ALTER TABLE fuel_settings ADD COLUMN IF NOT EXISTS offline_max_age_hours INT NOT NULL DEFAULT 72;
ALTER TABLE fuel_settings ADD COLUMN IF NOT EXISTS offline_clock_skew_minutes INT NOT NULL DEFAULT 5;
