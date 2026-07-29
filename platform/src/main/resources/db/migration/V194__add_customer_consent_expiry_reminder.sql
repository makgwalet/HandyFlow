-- Rename this file to the actual next Flyway version before running.

ALTER TABLE customer_consent
    ADD COLUMN expiry_reminder_sent_at TIMESTAMPTZ;