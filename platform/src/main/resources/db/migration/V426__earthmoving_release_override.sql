-- E3: a release that skipped the evidence checks (no completed work order since the machine went out of service, or released
-- by the person who finished the work) keeps the reason it was allowed.
ALTER TABLE earthmoving_status_events ADD COLUMN IF NOT EXISTS override_reason TEXT;
