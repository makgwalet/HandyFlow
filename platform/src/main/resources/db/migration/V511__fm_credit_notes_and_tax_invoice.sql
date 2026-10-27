-- Facilities management billing: credit notes, SA tax-invoice details, payment double-submit protection.
-- Idempotent (IF NOT EXISTS) so it is safe to re-run.

-- ── Tax-invoice details (VAT Act s20: supplier + recipient name, address, VAT number) ─────────────────────
ALTER TABLE fm_clients  ADD COLUMN IF NOT EXISTS vat_number VARCHAR(10);
ALTER TABLE fm_profiles ADD COLUMN IF NOT EXISTS address    VARCHAR(500);

-- Invoices carry a SNAPSHOT of both parties' details taken when the invoice is drafted, so changing a client's
-- address or VAT number later never rewrites an invoice that has already been issued.
ALTER TABLE fm_invoices ADD COLUMN IF NOT EXISTS supplier_name       VARCHAR(255);
ALTER TABLE fm_invoices ADD COLUMN IF NOT EXISTS supplier_vat_number VARCHAR(30);
ALTER TABLE fm_invoices ADD COLUMN IF NOT EXISTS supplier_address    VARCHAR(500);
ALTER TABLE fm_invoices ADD COLUMN IF NOT EXISTS client_name         VARCHAR(255);
ALTER TABLE fm_invoices ADD COLUMN IF NOT EXISTS client_vat_number   VARCHAR(10);
ALTER TABLE fm_invoices ADD COLUMN IF NOT EXISTS client_address      VARCHAR(500);
-- Total of APPROVED credit notes (VAT inclusive). Balance = total - amount_paid - credited_amount.
ALTER TABLE fm_invoices ADD COLUMN IF NOT EXISTS credited_amount     NUMERIC(15,2) NOT NULL DEFAULT 0;

-- ── Payment double-submit protection ──────────────────────────────────────────────────────────────────────
-- A client-supplied key makes "record payment" safe to retry: the same key on the same invoice returns the
-- original result instead of recording the money twice.
ALTER TABLE fm_invoice_payments ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);
CREATE UNIQUE INDEX IF NOT EXISTS uq_fm_payments_idempotency
    ON fm_invoice_payments (tenant_id, invoice_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
-- (A repeated bank reference is rejected in the service, not by an index: historic data may already hold repeats
-- and a unique index would fail this migration on a live database.)

-- ── Credit notes ──────────────────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS fm_credit_notes (
    id                     UUID PRIMARY KEY,
    tenant_id              UUID          NOT NULL,
    credit_note_number     VARCHAR(50)   NOT NULL,
    invoice_id             UUID          NOT NULL,
    client_id              UUID          NOT NULL,
    issue_date             DATE          NOT NULL,
    reason                 VARCHAR(500)  NOT NULL,
    subtotal               NUMERIC(15,2) NOT NULL,
    vat_rate               NUMERIC(5,2)  NOT NULL DEFAULT 0,
    vat_amount             NUMERIC(15,2) NOT NULL DEFAULT 0,
    total                  NUMERIC(15,2) NOT NULL,
    status                 VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',   -- DRAFT, APPROVED, CANCELLED
    prepared_by_user_id    UUID,
    approved_by_user_id    UUID,
    approved_at            TIMESTAMPTZ,
    cancelled_at           TIMESTAMPTZ,
    cancellation_reason    VARCHAR(500),
    -- snapshot of the parties, as on the original invoice
    supplier_name          VARCHAR(255),
    supplier_vat_number    VARCHAR(30),
    supplier_address       VARCHAR(500),
    client_name            VARCHAR(255),
    client_vat_number      VARCHAR(10),
    client_address         VARCHAR(500),
    gl_status              VARCHAR(20)   NOT NULL DEFAULT 'NOT_POSTED',
    gl_entry_id            UUID,
    gl_attempts            INTEGER       NOT NULL DEFAULT 0,
    gl_last_error          VARCHAR(500),
    gl_last_attempt_at     TIMESTAMPTZ,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version                BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_fm_credit_notes_number UNIQUE (credit_note_number),
    CONSTRAINT fk_fm_credit_note_invoice FOREIGN KEY (invoice_id) REFERENCES fm_invoices (id),
    CONSTRAINT chk_fm_credit_note_amounts CHECK (subtotal > 0 AND vat_amount >= 0 AND total = subtotal + vat_amount),
    CONSTRAINT chk_fm_credit_note_status CHECK (status IN ('DRAFT','APPROVED','CANCELLED'))
);
CREATE INDEX IF NOT EXISTS idx_fm_credit_notes_invoice ON fm_credit_notes (tenant_id, invoice_id);
CREATE INDEX IF NOT EXISTS idx_fm_credit_notes_client  ON fm_credit_notes (tenant_id, client_id, issue_date);
CREATE INDEX IF NOT EXISTS idx_fm_credit_notes_gl_pending ON fm_credit_notes (gl_status) WHERE gl_status IN ('PENDING','FAILED');
