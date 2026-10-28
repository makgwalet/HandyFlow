-- Recruiter: a change counter on applications, so a second person saving the same application at the same moment is told to reload
-- instead of silently replacing the first person's change.
ALTER TABLE rec_applications ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
