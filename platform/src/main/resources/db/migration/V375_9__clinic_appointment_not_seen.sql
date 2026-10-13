-- Patch 0202: NOT_SEEN, for a patient who arrived (checked in) but was never seen, so a past day's visit can be closed
-- honestly instead of staying "Checked in" for ever. Set by the nightly stale-visit sweep or by the front desk.
ALTER TABLE clinic_appointments DROP CONSTRAINT IF EXISTS clinic_appointments_status_check;
ALTER TABLE clinic_appointments
    ADD CONSTRAINT clinic_appointments_status_check
    CHECK (status IN ('SCHEDULED','CONFIRMED','CHECKED_IN','TRIAGED','IN_PROGRESS','COMPLETED','CANCELLED','NO_SHOW','NOT_SEEN'));
