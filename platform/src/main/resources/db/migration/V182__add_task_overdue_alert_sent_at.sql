-- Rename this file to match your actual next Flyway/Liquibase version number —
-- V999 is a placeholder so it doesn't collide with your real migration sequence.

ALTER TABLE tasks
    ADD COLUMN overdue_alert_sent_at TIMESTAMPTZ NULL;