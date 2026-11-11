-- Events Chunk 4: budget, supplier commitments and cost tracking.
-- RENAME to your next Flyway version (after Chunks 1-3), e.g. V215__events_budget_and_suppliers.sql
--
-- OWNERSHIP: these tables track the event's own planning and cost position.
-- They are NOT an accounting ledger. A cost entry can reference the
-- authoritative record (AP bill, expense claim, payment) through
-- source_module + source_ref; entries without one are reported as unreconciled.
-- All amounts are ZAR; VAT treatment follows whatever the entering user records
-- consistently (inclusive or exclusive) — it is not computed here.

-- ── One budget header per event ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS event_budgets (
    id                UUID          PRIMARY KEY,
    tenant_id         UUID          NOT NULL,
    event_id          UUID          NOT NULL UNIQUE REFERENCES events (id) ON DELETE CASCADE,
    status            VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',      -- DRAFT | SUBMITTED | APPROVED
    submitted_by      UUID,
    submitted_by_name VARCHAR(200),
    submitted_at      TIMESTAMPTZ,
    approved_by       UUID,
    approved_by_name  VARCHAR(200),
    approved_at       TIMESTAMPTZ,
    approved_total    NUMERIC(14,2),                               -- planned total at the moment of approval
    rejection_reason  VARCHAR(500),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_event_budgets_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED'))
);

-- ── Planned amounts by category ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS event_budget_lines (
    id              UUID          PRIMARY KEY,
    tenant_id       UUID          NOT NULL,
    event_id        UUID          NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    category        VARCHAR(30)   NOT NULL,
    description     VARCHAR(200)  NOT NULL,
    planned_amount  NUMERIC(14,2) NOT NULL,
    notes           VARCHAR(500),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_event_budget_lines_planned CHECK (planned_amount >= 0)
);
CREATE INDEX IF NOT EXISTS idx_event_budget_lines_event ON event_budget_lines (event_id, category, created_at);

-- ── Supplier quotes / commitments ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS event_commitments (
    id                  UUID          PRIMARY KEY,
    tenant_id           UUID          NOT NULL,
    event_id            UUID          NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    budget_line_id      UUID          NOT NULL REFERENCES event_budget_lines (id),
    vendor_id           UUID          REFERENCES event_vendors (id),
    description         VARCHAR(300)  NOT NULL,
    amount              NUMERIC(14,2) NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'QUOTED',   -- QUOTED | APPROVED | COMPLETED | CANCELLED
    quote_reference     VARCHAR(100),
    po_reference        VARCHAR(100),                              -- PO number/id in the purchasing module, if any
    contract_reference  VARCHAR(100),                              -- contract number/id in Contracting, if any
    deliverable         VARCHAR(500),
    due_date            DATE,
    deposit_amount      NUMERIC(14,2),
    deposit_due_date    DATE,
    approved_by         UUID,
    approved_by_name    VARCHAR(200),
    approved_at         TIMESTAMPTZ,
    over_budget_reason  VARCHAR(500),
    cancelled_reason    VARCHAR(500),
    created_by          UUID,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_event_commitments_status  CHECK (status IN ('QUOTED', 'APPROVED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_event_commitments_amount  CHECK (amount > 0),
    CONSTRAINT chk_event_commitments_deposit CHECK (deposit_amount IS NULL OR (deposit_amount >= 0 AND deposit_amount <= amount))
);
CREATE INDEX IF NOT EXISTS idx_event_commitments_event ON event_commitments (event_id, status);
CREATE INDEX IF NOT EXISTS idx_event_commitments_line  ON event_commitments (budget_line_id);

-- ── Invoices received and payments made ─────────────────────────────────────
-- Never deleted: a wrong entry is VOIDED with a reason, so history stays intact.
CREATE TABLE IF NOT EXISTS event_cost_entries (
    id                UUID          PRIMARY KEY,
    tenant_id         UUID          NOT NULL,
    event_id          UUID          NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    budget_line_id    UUID          NOT NULL REFERENCES event_budget_lines (id),
    commitment_id     UUID          REFERENCES event_commitments (id),
    kind              VARCHAR(10)   NOT NULL,                      -- INVOICE | PAYMENT
    is_deposit        BOOLEAN       NOT NULL DEFAULT FALSE,
    amount            NUMERIC(14,2) NOT NULL,
    entry_date        DATE          NOT NULL,
    reference         VARCHAR(100),                                -- supplier invoice no. / payment reference
    description       VARCHAR(300),
    source_module     VARCHAR(50),                                 -- e.g. 'accounting-ap', 'expenses'
    source_ref        VARCHAR(100),                                -- id/number of the authoritative record
    status            VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',     -- ACTIVE | VOIDED
    void_reason       VARCHAR(500),
    voided_by_name    VARCHAR(200),
    voided_at         TIMESTAMPTZ,
    recorded_by       UUID,
    recorded_by_name  VARCHAR(200),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_event_cost_entries_kind   CHECK (kind IN ('INVOICE', 'PAYMENT')),
    CONSTRAINT chk_event_cost_entries_status CHECK (status IN ('ACTIVE', 'VOIDED')),
    CONSTRAINT chk_event_cost_entries_amount CHECK (amount > 0),
    CONSTRAINT chk_event_cost_entries_deposit CHECK (is_deposit = FALSE OR kind = 'PAYMENT'),
    CONSTRAINT chk_event_cost_entries_source CHECK ((source_module IS NULL) = (source_ref IS NULL))
);
CREATE INDEX IF NOT EXISTS idx_event_cost_entries_event      ON event_cost_entries (event_id, status, entry_date DESC);
CREATE INDEX IF NOT EXISTS idx_event_cost_entries_commitment ON event_cost_entries (commitment_id) WHERE commitment_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_event_cost_entries_line       ON event_cost_entries (budget_line_id);

-- The same authoritative record can only be linked once per event (while active).
CREATE UNIQUE INDEX IF NOT EXISTS uq_event_cost_entries_source
    ON event_cost_entries (event_id, source_module, source_ref)
    WHERE status = 'ACTIVE' AND source_ref IS NOT NULL;