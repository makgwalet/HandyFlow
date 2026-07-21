-- V__PLACEHOLDER6__recruiter_interview_reminders.sql
-- RENAME: replace __PLACEHOLDER6__ with your real next Flyway version.
-- Independent of all prior recruiter migrations this session.
--
-- WHY a timestamp, not a boolean? "When" the reminder went out is cheap
-- to keep and occasionally useful (support/debugging "did they actually
-- get reminded"); costs nothing extra over a boolean flag.

ALTER TABLE rec_interviews
    ADD COLUMN reminder_sent_at TIMESTAMP;