-- Who started a consultation. Lets the drafts tray show a clinician only their own work.
-- Rows created before this migration have no recorded author and stay NULL.
ALTER TABLE clinic_consultations ADD COLUMN IF NOT EXISTS created_by UUID;
CREATE INDEX IF NOT EXISTS idx_clinic_consultations_created_by
    ON clinic_consultations (tenant_id, created_by) WHERE deleted_at IS NULL;
