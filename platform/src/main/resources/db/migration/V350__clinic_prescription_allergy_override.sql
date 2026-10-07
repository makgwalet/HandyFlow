-- When a prescription's medicine name matched a recorded allergy, the prescriber's reason for going ahead is kept
-- with the prescription (together with what matched), so the decision can be seen later. NULL = no match at the time.
ALTER TABLE clinic_prescriptions ADD COLUMN IF NOT EXISTS allergy_override_reason TEXT;
ALTER TABLE clinic_prescriptions ADD COLUMN IF NOT EXISTS allergy_alert_summary   TEXT;
