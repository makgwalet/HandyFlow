-- Claims money (CLINIC-DEC-001 to 004, 006, 007): an append-only ledger of what the scheme paid and of controlled
-- adjustments (write-offs, credit notes), plus void tracking. The claim's own amounts are no longer rewritten by a payment.

ALTER TABLE clinic_claims ADD COLUMN IF NOT EXISTS voided_at   TIMESTAMP;
ALTER TABLE clinic_claims ADD COLUMN IF NOT EXISTS voided_by   UUID;
ALTER TABLE clinic_claims ADD COLUMN IF NOT EXISTS void_reason TEXT;

DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'clinic_claims'::regclass AND contype = 'c' AND pg_get_constraintdef(oid) LIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE clinic_claims DROP CONSTRAINT %I', r.conname);
    END LOOP;
END $$;
ALTER TABLE clinic_claims ADD CONSTRAINT clinic_claims_status_check
    CHECK (status IN ('DRAFT','SUBMITTED','ACCEPTED','REJECTED','PAID','PARTIAL','CLOSED','VOIDED'));

-- A voided claim frees its consultation for a new claim.
DROP INDEX IF EXISTS idx_clinic_claims_consultation;
CREATE UNIQUE INDEX idx_clinic_claims_consultation ON clinic_claims(consultation_id) WHERE status <> 'VOIDED';

CREATE TABLE IF NOT EXISTS clinic_claim_scheme_payments (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL REFERENCES tenants(id),
    claim_id      UUID NOT NULL REFERENCES clinic_claims(id),
    amount        NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    received_on   DATE NOT NULL,
    reference     VARCHAR(100),
    batch_id      UUID,
    allocation    VARCHAR(20) NOT NULL DEFAULT 'SINGLE' CHECK (allocation IN ('SINGLE','OLDEST_FIRST','MANUAL','LEGACY')),
    override_reason TEXT,
    recorded_by   UUID,
    recorded_at   TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_clinic_claim_scheme_payments_claim ON clinic_claim_scheme_payments(tenant_id, claim_id);
CREATE INDEX IF NOT EXISTS idx_clinic_claim_scheme_payments_batch ON clinic_claim_scheme_payments(batch_id);

CREATE SEQUENCE IF NOT EXISTS clinic_credit_note_seq;

CREATE TABLE IF NOT EXISTS clinic_claim_adjustments (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    claim_id        UUID NOT NULL REFERENCES clinic_claims(id),
    kind            VARCHAR(12) NOT NULL CHECK (kind IN ('WRITE_OFF','CREDIT_NOTE')),
    amount          NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    reason          TEXT NOT NULL,
    credit_note_no  VARCHAR(20),
    authorised_by   UUID NOT NULL,
    adjusted_on     DATE NOT NULL,
    recorded_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    CHECK ((kind = 'CREDIT_NOTE') = (credit_note_no IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS idx_clinic_claim_adjustments_claim ON clinic_claim_adjustments(tenant_id, claim_id);

-- Append-only: a correction is a new row, never an edit.
CREATE OR REPLACE FUNCTION clinic_ledger_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only; post a correcting entry instead', TG_TABLE_NAME;
END $$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_clinic_claim_scheme_payments_append_only ON clinic_claim_scheme_payments;
CREATE TRIGGER trg_clinic_claim_scheme_payments_append_only
    BEFORE UPDATE OR DELETE ON clinic_claim_scheme_payments FOR EACH ROW EXECUTE FUNCTION clinic_ledger_append_only();
DROP TRIGGER IF EXISTS trg_clinic_claim_adjustments_append_only ON clinic_claim_adjustments;
CREATE TRIGGER trg_clinic_claim_adjustments_append_only
    BEFORE UPDATE OR DELETE ON clinic_claim_adjustments FOR EACH ROW EXECUTE FUNCTION clinic_ledger_append_only();

-- Claims already paid under the old model had scheme_portion overwritten with the amount the scheme paid. Record that
-- amount as one LEGACY payment so the scheme balance on those claims reads zero instead of looking unpaid.
INSERT INTO clinic_claim_scheme_payments (id, tenant_id, claim_id, amount, received_on, reference, allocation, recorded_at)
SELECT gen_random_uuid(), c.tenant_id, c.id, c.scheme_portion, c.updated_at::date, 'LEGACY', 'LEGACY', c.updated_at
FROM clinic_claims c
WHERE c.status IN ('PAID','PARTIAL') AND c.scheme_portion > 0
  AND NOT EXISTS (SELECT 1 FROM clinic_claim_scheme_payments p WHERE p.claim_id = c.id);
