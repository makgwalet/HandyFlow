-- V821: Legal Practice trust ledger controls. Confirm the next free Flyway version before applying (V819/V820 are Legal Compliance).
--
-- 1. balance_after: the client's trust balance after each movement. The screen always showed a dash in its "Balance after"
--    column because the list never carried one. Existing rows are backfilled from the running total; new rows are stamped
--    by LpTrustTransactionService in the same transaction that moves the balance.
-- 2. idempotency_key: lets the screen send one key per attempt so a double click or retry cannot post twice.
-- 3. The ledger becomes append-only in the database itself: no UPDATE and no DELETE, whoever runs it. Mistakes are
--    corrected by a new, opposite entry, never by editing history.

ALTER TABLE lp_trust_transactions
    ADD COLUMN IF NOT EXISTS balance_after   NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(80);

-- Backfill (runs before the append-only trigger exists). Ledger order is creation order.
UPDATE lp_trust_transactions t
   SET balance_after = r.running
  FROM (
        SELECT id,
               SUM(CASE WHEN transaction_type = 'RECEIPT' THEN amount ELSE -amount END)
                   OVER (PARTITION BY tenant_id, client_id ORDER BY created_at, id) AS running
          FROM lp_trust_transactions
       ) r
 WHERE t.id = r.id AND t.balance_after IS NULL;

ALTER TABLE lp_trust_transactions
    ADD CONSTRAINT ck_lp_trust_transactions_balance_after CHECK (balance_after IS NULL OR balance_after >= 0) NOT VALID;

CREATE UNIQUE INDEX IF NOT EXISTS uq_lp_trust_transactions_idempotency
    ON lp_trust_transactions (tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL;

-- Per-matter position, used when a matter is closed.
CREATE INDEX IF NOT EXISTS idx_lp_trust_transactions_matter
    ON lp_trust_transactions (tenant_id, matter_id) WHERE matter_id IS NOT NULL;

CREATE OR REPLACE FUNCTION lp_trust_transactions_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'lp_trust_transactions is append-only: % is not allowed. Post a correcting entry instead.', TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_lp_trust_transactions_append_only ON lp_trust_transactions;
CREATE TRIGGER trg_lp_trust_transactions_append_only
    BEFORE UPDATE OR DELETE ON lp_trust_transactions
    FOR EACH ROW EXECUTE FUNCTION lp_trust_transactions_append_only();
