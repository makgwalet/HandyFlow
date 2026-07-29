-- Rename this file to the actual next Flyway version before running.
--
-- FIX: adding "TELEHEALTH" as a bookable appointment type in the frontend
-- was incomplete — clinic_appointments_appointment_type_check at the DB
-- level still only allowed the original five values, so every TELEHEALTH
-- booking failed at INSERT with a 500, not a validation error.
--
-- ASSUMPTION: the original constraint's value list is inferred from the
-- five options already in ScheduleTab.tsx's booking form
-- (CONSULTATION/FOLLOW_UP/PROCEDURE/EMERGENCY/CHECKUP), not from having
-- seen the migration that created this constraint. If any other value was
-- ever allowed (e.g. a value used only via direct DB writes or a since-
-- removed frontend option), this migration will narrow the constraint
-- rather than only extend it — worth a quick check against your actual
-- data before running:
--   SELECT DISTINCT appointment_type FROM clinic_appointments;

ALTER TABLE clinic_appointments
    DROP CONSTRAINT clinic_appointments_appointment_type_check;

ALTER TABLE clinic_appointments
    ADD CONSTRAINT clinic_appointments_appointment_type_check
    CHECK (appointment_type IN ('CONSULTATION', 'FOLLOW_UP', 'PROCEDURE', 'EMERGENCY', 'CHECKUP', 'TELEHEALTH'));