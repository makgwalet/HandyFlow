-- The times a pump/POS provider reported for an imported shift. Kept beside, never instead of, the times this system recorded (opened_at / closed_at),
-- so reports and audit still use the system times. Null for shifts that were not imported.
ALTER TABLE fuel_shifts ADD COLUMN provider_opened_at TIMESTAMPTZ;
ALTER TABLE fuel_shifts ADD COLUMN provider_closed_at TIMESTAMPTZ;
