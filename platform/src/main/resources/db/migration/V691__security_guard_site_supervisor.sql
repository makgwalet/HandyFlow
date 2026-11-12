-- A guard a manager has made a site supervisor may see their current site's overview in the Guard app.
-- Checked from this column on every call, so switching it off takes effect immediately.
ALTER TABLE security_guards ADD COLUMN IF NOT EXISTS site_supervisor BOOLEAN NOT NULL DEFAULT FALSE;
