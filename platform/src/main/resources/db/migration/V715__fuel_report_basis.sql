-- Provider times in reports (decision 6). Off by default, so existing companies' reports do not change.
-- When on, an imported shift is dated by the times its pump/POS provider reported; the system's own times are kept separate and unchanged.
ALTER TABLE fuel_settings ADD COLUMN shift_report_use_provider_times BOOLEAN NOT NULL DEFAULT FALSE;
-- The timezone shifts-by-date reports use to decide which day a shift belongs to. Null means Africa/Johannesburg, as before.
ALTER TABLE fuel_settings ADD COLUMN report_timezone VARCHAR(60);
