-- V__PLACEHOLDER_ap_bill_due_soon_reminder.sql
-- RENAME: replace __PLACEHOLDER__ with your real next Flyway version.
--
-- WHY a timestamp flag, not just re-checking on every scheduler run? A
-- bill sitting in the "due soon" window for several days would otherwise
-- get re-notified every single time the scheduler fires — same idempotency
-- reasoning as RecInterview.reminderSentAt in the recruiter module's
-- InterviewReminderScheduler. This is a one-time "heads up" reminder, not
-- a recurring nag.

ALTER TABLE ap_bills
    ADD COLUMN due_soon_reminder_sent_at TIMESTAMP;