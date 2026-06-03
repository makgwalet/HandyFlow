-- V42__pos_enhancements.sql
-- Adds: cash sessions, split payment column on transactions,
-- refund linkage, session linkage, vat_exempt on catalogue items,
-- and populates pos_stock_adjustments (table already in V41).
--
-- These are additive changes — no existing data is touched.

-- ── 1. VAT exempt flag on catalogue items ─────────────────────────────────────
-- Zero-rated items (basic food, exports) must not charge VAT.
ALTER TABLE catalogue_items
    ADD COLUMN IF NOT EXISTS vat_exempt BOOLEAN NOT NULL DEFAULT false;

-- ── 2. Cash sessions ──────────────────────────────────────────────────────────
-- A cashier opens a session with a float at start of shift, closes it at end
-- with a physical count. Variance = closing - (opening + expected).
CREATE TABLE IF NOT EXISTS pos_cash_sessions (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    session_number   VARCHAR(20)  NOT NULL,
    opened_by        UUID         NOT NULL REFERENCES users(id),
    opened_by_name   VARCHAR(255) NOT NULL,
    closed_by        UUID         REFERENCES users(id),
    closed_by_name   VARCHAR(255),
    opening_float    NUMERIC(15,2) NOT NULL DEFAULT 0,
    closing_float    NUMERIC(15,2),
    expected_cash    NUMERIC(15,2),   -- sum of CASH sales during session
    cash_variance    NUMERIC(15,2),   -- closing_float - (opening_float + expected_cash)
    total_sales      NUMERIC(15,2)    NOT NULL DEFAULT 0,
    transaction_count INT           NOT NULL DEFAULT 0,
    status           VARCHAR(10)   NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN','CLOSED')),
    notes            TEXT,
    opened_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    closed_at        TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_pos_cash_sessions    PRIMARY KEY (id),
    CONSTRAINT uq_pos_session_number   UNIQUE (tenant_id, session_number)
);

CREATE INDEX IF NOT EXISTS idx_pos_sessions_tenant
    ON pos_cash_sessions(tenant_id, opened_at DESC);

CREATE INDEX IF NOT EXISTS idx_pos_sessions_open
    ON pos_cash_sessions(tenant_id, status)
    WHERE status = 'OPEN';

-- ── 3. Extend pos_transactions ────────────────────────────────────────────────

-- Split payment JSON (stored as text blob)
ALTER TABLE pos_transactions
    ADD COLUMN IF NOT EXISTS split_payments_json TEXT;

-- Cash session linkage
ALTER TABLE pos_transactions
    ADD COLUMN IF NOT EXISTS cash_session_id UUID REFERENCES pos_cash_sessions(id) ON DELETE SET NULL;

-- Refund linkage — the original transaction that this refund is against
ALTER TABLE pos_transactions
    ADD COLUMN IF NOT EXISTS original_transaction_id UUID REFERENCES pos_transactions(id) ON DELETE SET NULL;

ALTER TABLE pos_transactions
    ADD COLUMN IF NOT EXISTS refund_reason TEXT;

-- Add REFUND to status constraint (recreate — cannot add values to existing CHECK in Postgres)
-- Drop old constraint and re-add with REFUND included
ALTER TABLE pos_transactions
    DROP CONSTRAINT IF EXISTS pos_transactions_status_check;

ALTER TABLE pos_transactions
    ADD CONSTRAINT pos_transactions_status_check
        CHECK (status IN ('DRAFT','COMPLETED','VOIDED','REFUNDED'));

-- Index for refund lookups
CREATE INDEX IF NOT EXISTS idx_pos_txn_original
    ON pos_transactions(original_transaction_id)
    WHERE original_transaction_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pos_txn_session
    ON pos_transactions(cash_session_id)
    WHERE cash_session_id IS NOT NULL;

-- ── 4. Ensure pos_stock_adjustments columns exist (V41 may have created the table) ──
-- pos_stock_adjustments and pos_adjustment_items were defined in V41.
-- Just verify the indexes exist.
CREATE INDEX IF NOT EXISTS idx_pos_adjustments_tenant
    ON pos_stock_adjustments(tenant_id, created_at DESC);

-- ── 5. Additional permissions ─────────────────────────────────────────────────
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'POS_CASHIER',  'Open and close cash sessions, process CASH sales'),
    (gen_random_uuid(), 'POS_REFUND',   'Process refunds against completed transactions')
ON CONFLICT (name) DO NOTHING;

-- Give ADMIN all POS permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('POS_CASHIER', 'POS_REFUND')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
