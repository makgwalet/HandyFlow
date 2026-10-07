-- Clinic Sprint 1 (S1-8): front-desk/nurse progress states. IN_PROGRESS remains the in-consultation state.
ALTER TABLE clinic_appointments DROP CONSTRAINT clinic_appointments_status_check;
ALTER TABLE clinic_appointments
    ADD CONSTRAINT clinic_appointments_status_check
    CHECK (status IN ('SCHEDULED','CONFIRMED','CHECKED_IN','TRIAGED','IN_PROGRESS','COMPLETED','CANCELLED','NO_SHOW'));
ALTER TABLE clinic_appointments
    ADD COLUMN checked_in_at TIMESTAMP,
    ADD COLUMN triaged_at    TIMESTAMP;
