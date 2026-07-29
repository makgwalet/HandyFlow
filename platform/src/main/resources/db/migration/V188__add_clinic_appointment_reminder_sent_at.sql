-- Rename this file to the actual next Flyway version before running.

ALTER TABLE clinic_appointments
    ADD COLUMN reminder_sent_at TIMESTAMPTZ;

CREATE INDEX idx_clinic_appointments_reminder_sweep
    ON clinic_appointments (status, scheduled_at)
    WHERE reminder_sent_at IS NULL AND deleted_at IS NULL;