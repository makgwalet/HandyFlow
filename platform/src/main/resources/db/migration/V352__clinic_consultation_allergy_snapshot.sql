-- The allergies on record when a consultation was signed, kept with the consultation so the record shows what was
-- known at that visit even after the patient's allergy list changes. NULL = not captured (older consultations);
-- an empty "items" list = no active allergies were recorded at the time.
ALTER TABLE clinic_consultations ADD COLUMN IF NOT EXISTS allergy_snapshot JSONB;
