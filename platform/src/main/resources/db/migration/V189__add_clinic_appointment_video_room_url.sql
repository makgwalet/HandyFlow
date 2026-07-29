-- Rename this file to the actual next Flyway version before running.

ALTER TABLE clinic_appointments
    ADD COLUMN video_room_url VARCHAR(500);