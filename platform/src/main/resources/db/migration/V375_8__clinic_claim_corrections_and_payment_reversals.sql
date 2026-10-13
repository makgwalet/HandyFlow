-- Claims, second half (patch 0198).
--  1. A corrected claim points back at the claim it replaces (CLINIC-DEC-003/004).
--  2. A scheme payment received in error is reversed by an append-only row that points at that payment; the payment row itself is never touched.
--  3. The reversal posts the opposite journal, so the money-posting table accepts the new event type.

ALTER TABLE clinic_claims ADD COLUMN IF NOT EXISTS corrects_claim_id UUID REFERENCES clinic_claims(id);
CREATE INDEX IF NOT EXISTS idx_clinic_claims_corrects ON clinic_claims(tenant_id, corrects_claim_id) WHERE corrects_claim_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS clinic_claim_payment_reversals (
    id           UUID PRIMARY KEY,
    tenant_id    UUID          NOT NULL REFERENCES tenants(id),
    claim_id     UUID          NOT NULL REFERENCES clinic_claims(id),
    payment_id   UUID          NOT NULL REFERENCES clinic_claim_scheme_payments(id),
    amount       NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    reason       TEXT          NOT NULL,
    reversed_by  UUID          NOT NULL,
    reversed_on  DATE          NOT NULL,
    recorded_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_clinic_claim_payment_reversal UNIQUE (payment_id)   -- a payment can be reversed once
);
CREATE INDEX IF NOT EXISTS idx_clinic_claim_payment_reversals_claim ON clinic_claim_payment_reversals(tenant_id, claim_id);

DROP TRIGGER IF EXISTS trg_clinic_claim_payment_reversals_append_only ON clinic_claim_payment_reversals;
CREATE TRIGGER trg_clinic_claim_payment_reversals_append_only
    BEFORE UPDATE OR DELETE ON clinic_claim_payment_reversals FOR EACH ROW EXECUTE FUNCTION clinic_ledger_append_only();

-- What the scheme has really paid: each payment counts once, each reversal takes it back. Signed amounts, one row per movement.
CREATE OR REPLACE VIEW clinic_claim_net_payments AS
    SELECT id, tenant_id, claim_id, amount, recorded_at FROM clinic_claim_scheme_payments
    UNION ALL
    SELECT id, tenant_id, claim_id, -amount, recorded_at FROM clinic_claim_payment_reversals;

DO $$
DECLARE r RECORD;
BEGIN
    FOR r IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'clinic_money_posting'::regclass AND contype = 'c' AND pg_get_constraintdef(oid) LIKE '%event_type%'
    LOOP
        EXECUTE format('ALTER TABLE clinic_money_posting DROP CONSTRAINT %I', r.conname);
    END LOOP;
END $$;
ALTER TABLE clinic_money_posting ADD CONSTRAINT clinic_money_posting_event_type_check
    CHECK (event_type IN ('SCHEME_PAYMENT_RECEIVED','CLAIM_WRITTEN_OFF','SCHEME_PAYMENT_REVERSED'));
