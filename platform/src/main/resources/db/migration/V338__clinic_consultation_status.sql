-- Clinic Sprint 0: consultations become a persisted, stateful aggregate.
-- Existing rows were all recorded in one shot, so they are SIGNED.
ALTER TABLE clinic_consultations
    ADD COLUMN status    VARCHAR(20) NOT NULL DEFAULT 'SIGNED',
    ADD COLUMN signed_at TIMESTAMP;

UPDATE clinic_consultations SET signed_at = consulted_at WHERE signed_at IS NULL;

ALTER TABLE clinic_consultations
    ADD CONSTRAINT chk_clinic_consultation_status
    CHECK (status IN ('DRAFT','SIGNED','LOCKED','ABANDONED'));

CREATE INDEX idx_clinic_consultations_drafts
    ON clinic_consultations (tenant_id, practitioner_id)
    WHERE status = 'DRAFT' AND deleted_at IS NULL;
