-- Merging two patient records that are the same person (patch 0188, CLINIC-DEC permission CLINIC_PATIENT_MERGE).
-- The record merged away is archived, never deleted: clinical records are kept (HPCSA). Everything attached to it moves to the
-- record that stays, in one transaction, and this table records what moved so the merge can be examined later (and undone by hand if need be).

ALTER TABLE clinic_patients ADD COLUMN IF NOT EXISTS merged_into UUID REFERENCES clinic_patients(id);

CREATE TABLE IF NOT EXISTS clinic_patient_merges (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID         NOT NULL REFERENCES tenants(id),
    survivor_id           UUID         NOT NULL REFERENCES clinic_patients(id),
    merged_id             UUID         NOT NULL REFERENCES clinic_patients(id),
    merged_by             UUID,
    merged_at             TIMESTAMP    NOT NULL DEFAULT now(),
    reason                VARCHAR(500) NOT NULL CHECK (length(btrim(reason)) >= 5),
    acknowledged_conflicts BOOLEAN     NOT NULL DEFAULT FALSE,
    conflicts             JSONB        NOT NULL DEFAULT '[]'::jsonb,
    filled_columns        JSONB        NOT NULL DEFAULT '[]'::jsonb,
    moved                 JSONB        NOT NULL DEFAULT '{}'::jsonb,   -- table -> ids of the rows that moved
    not_moved             JSONB        NOT NULL DEFAULT '{}'::jsonb,   -- table -> why they stayed with the archived record
    survivor_before       JSONB        NOT NULL,
    merged_before         JSONB        NOT NULL,
    CONSTRAINT chk_patient_merge_distinct CHECK (survivor_id <> merged_id)
);
-- A record can be merged away once.
CREATE UNIQUE INDEX IF NOT EXISTS uq_clinic_patient_merges_merged ON clinic_patient_merges (tenant_id, merged_id);
CREATE INDEX IF NOT EXISTS idx_clinic_patient_merges_survivor ON clinic_patient_merges (tenant_id, survivor_id, merged_at DESC);

CREATE OR REPLACE FUNCTION clinic_patient_merges_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'clinic_patient_merges is append-only';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_clinic_patient_merges_append_only ON clinic_patient_merges;
CREATE TRIGGER trg_clinic_patient_merges_append_only
    BEFORE UPDATE OR DELETE ON clinic_patient_merges FOR EACH ROW EXECUTE FUNCTION clinic_patient_merges_append_only();
