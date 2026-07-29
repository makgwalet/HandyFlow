-- Rename this file to match your actual next Flyway/Liquibase version number —
-- placeholder, same caveat as the other pending migrations from this session.

ALTER TABLE fuel_deliveries
    ADD COLUMN reminder_sent_at TIMESTAMPTZ NULL,
    ADD COLUMN overdue_alert_sent_at TIMESTAMPTZ NULL;