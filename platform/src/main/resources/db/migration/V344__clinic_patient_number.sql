-- Clinic Sprint 1 (S1-7): a stable, human-friendly patient number per tenant (P000001, P000002, ...).
ALTER TABLE clinic_patients ADD COLUMN patient_number VARCHAR(20);

-- Backfill existing patients in creation order, per tenant.
UPDATE clinic_patients p
SET patient_number = 'P' || LPAD(n.rn::text, 6, '0')
FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY tenant_id ORDER BY created_at, id) AS rn
      FROM clinic_patients) n
WHERE p.id = n.id;

CREATE UNIQUE INDEX uq_clinic_patient_number ON clinic_patients (tenant_id, patient_number);

-- Next number to hand out, per tenant (atomic upsert in application code).
CREATE TABLE clinic_patient_counters (
    tenant_id  UUID    PRIMARY KEY REFERENCES tenants(id),
    next_value BIGINT  NOT NULL
);
INSERT INTO clinic_patient_counters (tenant_id, next_value)
SELECT tenant_id, COUNT(*) + 1 FROM clinic_patients GROUP BY tenant_id;

-- Exact-ID lookups (duplicate detection) must be fast. Not unique: legacy data may already hold repeats.
CREATE INDEX idx_clinic_patients_id_number ON clinic_patients (tenant_id, id_number)
    WHERE id_number IS NOT NULL AND deleted_at IS NULL;
