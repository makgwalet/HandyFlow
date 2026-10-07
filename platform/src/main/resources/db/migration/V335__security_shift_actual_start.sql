-- V335__security_shift_actual_start.sql
--
-- The moment a shift was really started (a supervisor starting it, or the guard opening a device session at the post).
-- Punctuality is judged from this against the scheduled start. Shifts that started before this column existed keep
-- NULL, and are judged on the late-arrival alert as before. Stored like start_at: UTC, without time zone.

ALTER TABLE security_shifts ADD COLUMN actual_start_at TIMESTAMP;

COMMENT ON COLUMN security_shifts.actual_start_at IS 'When the shift was actually started (UTC). NULL for shifts started before V335 or not yet started.';
