-- V__PLACEHOLDER7__recruiter_interview_reschedule.sql
-- RENAME: replace __PLACEHOLDER7__ with your real next Flyway version.
-- Independent of all prior recruiter migrations this session.
--
-- WHY a self-referencing FK rather than mutating scheduledAt in place?
-- Matches the audit-trail ethos already used everywhere else in this
-- module (RecStageHistory, offer-letter-sent tracking) — a rescheduled
-- interview keeps existing as a real historical row (outcome=RESCHEDULED,
-- reschedule_reason explaining why) rather than being silently overwritten.
-- The replacement interview links back via rescheduled_from_interview_id.
--
-- reschedule_reason is deliberately separate from the existing "notes"
-- column — notes is for the interviewer's actual assessment after
-- conducting the interview; a rescheduled interview never happened, so
-- there's no real outcome notes to record for it, and conflating the two
-- would risk the reschedule reason getting silently overwritten if that
-- row's notes were ever edited for an unrelated reason.

ALTER TABLE rec_interviews
    ADD COLUMN reschedule_reason             TEXT,
    ADD COLUMN rescheduled_from_interview_id UUID REFERENCES rec_interviews(id);