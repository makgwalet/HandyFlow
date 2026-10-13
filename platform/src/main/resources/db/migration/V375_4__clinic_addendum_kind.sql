-- Addenda (S1-1, patch 0187): what kind of addendum it is, and nobody can edit or delete one afterwards.
-- LATE_MEDICINE is written by the system when a medicine is added after signing; the others are chosen by the person adding it.

ALTER TABLE clinic_consultation_addenda ADD COLUMN IF NOT EXISTS kind VARCHAR(20) NOT NULL DEFAULT 'OTHER';

-- Rows written before this patch: the system's late-medicine lines are recognisable by their wording.
-- (Done before the append-only trigger exists; a re-run finds the trigger and must not touch rows.)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_clinic_addenda_append_only') THEN
        UPDATE clinic_consultation_addenda SET kind = 'LATE_MEDICINE' WHERE kind = 'OTHER' AND text LIKE 'Prescription added after signing:%';
    END IF;
END $$;

ALTER TABLE clinic_consultation_addenda DROP CONSTRAINT IF EXISTS chk_clinic_addenda_kind;
ALTER TABLE clinic_consultation_addenda ADD CONSTRAINT chk_clinic_addenda_kind
    CHECK (kind IN ('CORRECTION','ADDITIONAL_INFO','RESULT','LATE_ENTRY','LATE_MEDICINE','OTHER'));

CREATE OR REPLACE FUNCTION clinic_addenda_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'clinic_consultation_addenda is append-only';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_clinic_addenda_append_only ON clinic_consultation_addenda;
CREATE TRIGGER trg_clinic_addenda_append_only
    BEFORE UPDATE OR DELETE ON clinic_consultation_addenda FOR EACH ROW EXECUTE FUNCTION clinic_addenda_append_only();
