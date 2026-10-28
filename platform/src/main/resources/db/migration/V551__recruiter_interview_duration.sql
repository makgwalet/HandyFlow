-- How long an interview runs, so overlapping bookings for the same interviewer, panelist or candidate can be found.
-- Existing interviews keep the one-hour length the screens have always assumed.
ALTER TABLE rec_interviews ADD COLUMN IF NOT EXISTS duration_minutes INT NOT NULL DEFAULT 60;
ALTER TABLE rec_interviews ADD CONSTRAINT chk_rec_interviews_duration CHECK (duration_minutes BETWEEN 15 AND 480);

CREATE INDEX IF NOT EXISTS idx_rec_interviews_pending_time ON rec_interviews (tenant_id, scheduled_at) WHERE outcome = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_rec_interview_panelists_user ON rec_interview_panelists (user_id);
