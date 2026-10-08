-- Who signed a consultation (patch 0160). Rows signed before this stay null and show "not recorded".
ALTER TABLE clinic_consultations ADD COLUMN IF NOT EXISTS signed_by UUID;
