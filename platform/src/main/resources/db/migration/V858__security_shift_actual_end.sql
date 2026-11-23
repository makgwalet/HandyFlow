-- When the guard actually ended the shift (UTC, like actual_start_at). Empty for shifts closed before this change, and for shifts a
-- supervisor force-closed or pulled, because the guard's real departure time is not known for those.
ALTER TABLE security_shifts ADD COLUMN IF NOT EXISTS actual_end_at TIMESTAMP;
COMMENT ON COLUMN security_shifts.actual_end_at IS 'When the guard clocked out (UTC). NULL when not recorded: older shifts, force-closed or pulled shifts.';
