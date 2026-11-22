-- What the fleet alert job has already told people about, per phone, so one problem is one alert (plus a reminder).
ALTER TABLE security_devices ADD COLUMN IF NOT EXISTS fleet_alerted_issues VARCHAR(100);
ALTER TABLE security_devices ADD COLUMN IF NOT EXISTS fleet_alerted_at TIMESTAMPTZ;
