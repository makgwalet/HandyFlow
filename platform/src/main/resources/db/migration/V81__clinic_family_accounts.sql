-- WHY this migration?
-- P1: Family accounts need a self-referencing principal_id so dependants
--     link back to their principal patient row.
-- P2: account_type distinguishes INDIVIDUAL / PRINCIPAL / DEPENDANT so
--     the patient list can show the right badge without a join.
-- P3: archived_at / archive_reason enable HPCSA-compliant soft archiving —
--     records are NEVER hard-deleted; the HPCSA requires retention for
--     6 years (adults) and until age 21 for minors.
-- P4: last_visit_at is a denormalised cache updated by the consultation
--     service on every save — avoids a slow MAX(consulted_at) join on
--     every patient list page load.

ALTER TABLE clinic_patients
  ADD COLUMN IF NOT EXISTS principal_id    UUID        REFERENCES clinic_patients(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS relationship    VARCHAR(20) CHECK (relationship IN
    ('CHILD','PARENT','GRANDPARENT','SPOUSE','SIBLING','OTHER')),
  ADD COLUMN IF NOT EXISTS account_type   VARCHAR(20) NOT NULL DEFAULT 'INDIVIDUAL'
    CHECK (account_type IN ('INDIVIDUAL','PRINCIPAL','DEPENDANT')),
  ADD COLUMN IF NOT EXISTS archived_at     TIMESTAMP,
  ADD COLUMN IF NOT EXISTS archive_reason  TEXT,
  ADD COLUMN IF NOT EXISTS last_visit_at   TIMESTAMP;

-- Partial index: fast lookup of all dependants under a principal
CREATE INDEX IF NOT EXISTS idx_clinic_patients_principal
  ON clinic_patients(principal_id)
  WHERE principal_id IS NOT NULL;

-- Partial index: filter archived patients quickly
CREATE INDEX IF NOT EXISTS idx_clinic_patients_archived
  ON clinic_patients(tenant_id, archived_at)
  WHERE archived_at IS NOT NULL;

-- WHY update existing rows?
-- All pre-existing patients were registered as individuals before the
-- family account feature existed — set them explicitly so the CHECK
-- constraint is satisfied and the frontend badge renders correctly.
UPDATE clinic_patients
  SET account_type = 'INDIVIDUAL'
  WHERE account_type IS NULL OR account_type = '';
