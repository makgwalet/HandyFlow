-- The app version each phone last reported, so a supervisor can see which phones run an old build.
ALTER TABLE security_devices ADD COLUMN IF NOT EXISTS app_version VARCHAR(30);
