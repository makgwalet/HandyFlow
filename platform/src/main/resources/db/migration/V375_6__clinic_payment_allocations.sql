-- Patient and scheme receivables are separate (CLINIC-DEC-005, patch 0190). The scheme's money is tracked per claim by the claim ledger (V365).
-- This table does the same for the patient's own money: each patient payment is split over the visits it pays for, so every visit shows what the
-- patient has paid on it and what is still owed. A payment's part that is not yet linked to a visit stays "on account".
-- Append-only: who linked how much of which payment to which claim, and when, cannot be edited or removed.

CREATE TABLE IF NOT EXISTS clinic_payment_allocations (
    id           UUID PRIMARY KEY,
    tenant_id    UUID          NOT NULL REFERENCES tenants(id),
    payment_id   UUID          NOT NULL REFERENCES clinic_payments(id),
    claim_id     UUID          NOT NULL REFERENCES clinic_claims(id),
    patient_id   UUID          NOT NULL REFERENCES clinic_patients(id),
    amount       NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    allocated_at TIMESTAMP     NOT NULL DEFAULT now(),
    allocated_by UUID
);
CREATE INDEX IF NOT EXISTS idx_clinic_payment_alloc_claim   ON clinic_payment_allocations (tenant_id, claim_id);
CREATE INDEX IF NOT EXISTS idx_clinic_payment_alloc_payment ON clinic_payment_allocations (tenant_id, payment_id);
CREATE INDEX IF NOT EXISTS idx_clinic_payment_alloc_patient ON clinic_payment_allocations (tenant_id, patient_id);

CREATE OR REPLACE FUNCTION clinic_payment_allocations_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'clinic_payment_allocations is append-only';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_clinic_payment_allocations_append_only ON clinic_payment_allocations;
CREATE TRIGGER trg_clinic_payment_allocations_append_only
    BEFORE UPDATE OR DELETE ON clinic_payment_allocations FOR EACH ROW EXECUTE FUNCTION clinic_payment_allocations_append_only();
