-- V___creative_notifications.sql
-- (rename to the next available Flyway version number in your sequence)
--
-- Backs the five notification gaps flagged in the Creative module review:
--   viewed_at              -> first-view "client has seen it" signal + notification
--   reminder_sent_at       -> idempotency for the unapproved-proof reminder
--   overdue_alert_sent_at  -> idempotency for the overdue-job alert
-- (Client-comment and deliverable-ready notifications needed no schema
-- change — they fire inline on the existing addClientComment/addDeliverable
-- request path.)

ALTER TABLE cre_proofs
    ADD COLUMN IF NOT EXISTS viewed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS reminder_sent_at TIMESTAMP;

ALTER TABLE cre_jobs
    ADD COLUMN IF NOT EXISTS overdue_alert_sent_at TIMESTAMP;
