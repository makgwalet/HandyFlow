-- Chunk 7: Facilities Management billing integrity — VAT, invoice approval, payment records,
-- ledger-posting status/retry, and cost-vs-charge for margin.
-- RENAME to the next free Flyway version; apply AFTER the Chunk 6 migration.
--
-- IMPORTANT: gen_random_uuid() below needs PostgreSQL 13+ (built in) or the pgcrypto extension.

-- 1. Billing settings live on the FM company profile. Defaults reproduce today's behaviour
--    (no VAT, 30-day terms, accounts 1100/4000), so existing tenants are unaffected until they configure VAT.
ALTER TABLE fm_profiles
    ADD COLUMN IF NOT EXISTS vat_registered       BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS vat_number           VARCHAR(30),
    ADD COLUMN IF NOT EXISTS vat_rate             NUMERIC(5, 2) NOT NULL DEFAULT 15.00,
    ADD COLUMN IF NOT EXISTS payment_terms_days   INTEGER       NOT NULL DEFAULT 30,
    ADD COLUMN IF NOT EXISTS ar_account_code      VARCHAR(20)   NOT NULL DEFAULT '1100',
    ADD COLUMN IF NOT EXISTS revenue_account_code VARCHAR(20)   NOT NULL DEFAULT '4000',
    ADD COLUMN IF NOT EXISTS vat_account_code     VARCHAR(20),
    ADD COLUMN IF NOT EXISTS bank_account_code    VARCHAR(20);

-- 2. Invoices: VAT snapshot, preparer/approver, cancellation, and ledger-posting status.
--    gl_status: NOT_POSTED (draft) -> PENDING (approved, awaiting post) -> POSTED | FAILED (retried automatically)
--               LEGACY = created under the old flow, which posted at generation; never re-posted.
ALTER TABLE fm_invoices
    ADD COLUMN IF NOT EXISTS vat_rate            NUMERIC(5, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS prepared_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS approved_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS approved_at         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancelled_at        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS gl_status           VARCHAR(20)   NOT NULL DEFAULT 'NOT_POSTED',
    ADD COLUMN IF NOT EXISTS gl_entry_id         UUID,
    ADD COLUMN IF NOT EXISTS gl_attempts         INTEGER       NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS gl_last_error       VARCHAR(500),
    ADD COLUMN IF NOT EXISTS gl_last_attempt_at  TIMESTAMPTZ;

UPDATE fm_invoices SET gl_status = 'LEGACY' WHERE gl_status = 'NOT_POSTED';
UPDATE fm_invoices SET approved_at = COALESCE(sent_at, created_at)
 WHERE status IN ('SENT', 'PARTIAL', 'PAID') AND approved_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_fm_invoices_gl_pending ON fm_invoices (gl_status) WHERE gl_status IN ('PENDING', 'FAILED');

-- 3. Payment records (history), each with its own ledger-posting status.
CREATE TABLE IF NOT EXISTS fm_invoice_payments (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID           NOT NULL,
    invoice_id          UUID           NOT NULL,
    amount              NUMERIC(15, 2) NOT NULL,
    payment_date        DATE           NOT NULL,
    method              VARCHAR(30),
    reference           VARCHAR(100),
    recorded_by_user_id UUID,
    created_at          TIMESTAMPTZ    NOT NULL,
    gl_status           VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    gl_entry_id         UUID,
    gl_attempts         INTEGER        NOT NULL DEFAULT 0,
    gl_last_error       VARCHAR(500),
    gl_last_attempt_at  TIMESTAMPTZ,
    CONSTRAINT fk_fm_payment_invoice FOREIGN KEY (invoice_id) REFERENCES fm_invoices (id),
    CONSTRAINT chk_fm_payment_positive CHECK (amount > 0)
);
CREATE INDEX IF NOT EXISTS idx_fm_payments_invoice ON fm_invoice_payments (tenant_id, invoice_id);
CREATE INDEX IF NOT EXISTS idx_fm_payments_gl_pending ON fm_invoice_payments (gl_status) WHERE gl_status IN ('PENDING', 'FAILED');

-- Backfill one LEGACY payment per already-paid invoice so payment history matches amount_paid.
-- LEGACY payments are never posted to the ledger (they were not under the old flow either).
INSERT INTO fm_invoice_payments (id, tenant_id, invoice_id, amount, payment_date, method, reference, created_at, gl_status)
SELECT gen_random_uuid(), tenant_id, id, amount_paid, COALESCE(paid_at, created_at)::date, 'LEGACY', 'Recorded before payment history existed',
       COALESCE(paid_at, created_at), 'LEGACY'
  FROM fm_invoices
 WHERE amount_paid > 0;

-- 4. Work orders: link to the invoice that billed them (lets a DRAFT invoice be cancelled and its work
--    released) and keep the INTERNAL cost separate from the billed charge ("cost" stays the billed amount).
ALTER TABLE fm_work_orders
    ADD COLUMN IF NOT EXISTS invoice_id    UUID,
    ADD COLUMN IF NOT EXISTS internal_cost NUMERIC(15, 2);
CREATE INDEX IF NOT EXISTS idx_fm_wo_invoice ON fm_work_orders (tenant_id, invoice_id);
CREATE INDEX IF NOT EXISTS idx_fm_wo_client_completed ON fm_work_orders (tenant_id, client_id, completed_at);

-- 5. Cost lines: cost (what it costs the FM company) vs charge (what the client pays).
--    Existing lines get charge = cost, so current invoicing results are unchanged.
ALTER TABLE fm_work_order_cost_lines
    ADD COLUMN IF NOT EXISTS unit_charge  NUMERIC(15, 2),
    ADD COLUMN IF NOT EXISTS total_charge NUMERIC(15, 2);
UPDATE fm_work_order_cost_lines SET unit_charge = unit_cost, total_charge = total_cost WHERE unit_charge IS NULL;
ALTER TABLE fm_work_order_cost_lines ALTER COLUMN unit_charge SET NOT NULL;
ALTER TABLE fm_work_order_cost_lines ALTER COLUMN total_charge SET NOT NULL;

-- Work completed before this chunk never recorded an internal cost. Back-fill it equal to the billed
-- amount (zero margin) so profitability reports don't treat historic work as free. Real costs apply from now on.
UPDATE fm_work_orders SET internal_cost = cost
 WHERE status = 'COMPLETED' AND internal_cost IS NULL AND cost IS NOT NULL;