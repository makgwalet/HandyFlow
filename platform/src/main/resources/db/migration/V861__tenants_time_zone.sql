-- The IANA time zone a company's business days, weeks and months are decided in. Moments stay stored in UTC.
-- Default Africa/Johannesburg; a company in Namibia or Botswana can be set to its own zone later.
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS time_zone VARCHAR(60) NOT NULL DEFAULT 'Africa/Johannesburg';
ALTER TABLE tenants DROP CONSTRAINT IF EXISTS chk_tenants_time_zone;
ALTER TABLE tenants ADD CONSTRAINT chk_tenants_time_zone CHECK (time_zone ~ '^[A-Za-z][A-Za-z0-9_+-]*(/[A-Za-z0-9_+-]+){0,2}$');
COMMENT ON COLUMN tenants.time_zone IS 'IANA zone id used by BusinessCalendar to decide which business day, week or month a moment belongs to.';
