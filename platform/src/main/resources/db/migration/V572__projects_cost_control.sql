-- Chunk 4: cost control — append-only cost ledger, budget revisions, original budget, forecast.
-- Rename with your next Flyway version number. Requires PostgreSQL 13+ (gen_random_uuid()).

ALTER TABLE project_budget_lines
    ADD COLUMN IF NOT EXISTS original_amount       NUMERIC(15,2),   -- NULL = added after the baseline was locked
    ADD COLUMN IF NOT EXISTS forecast_to_complete  NUMERIC(15,2);   -- manual ETC; NULL = use remaining budget

CREATE TABLE IF NOT EXISTS project_cost_entries (
    id               UUID PRIMARY KEY,
    tenant_id        UUID          NOT NULL,
    project_id       UUID          NOT NULL REFERENCES projects(id),
    budget_line_id   UUID          NOT NULL REFERENCES project_budget_lines(id),
    entry_type       VARCHAR(20)   NOT NULL,
    amount           NUMERIC(15,2) NOT NULL,           -- signed: credits / reversals are negative
    source_type      VARCHAR(30)   NOT NULL,           -- PO | INVOICE | EXPENSE | TIMESHEET | MANUAL | OPENING ...
    source_ref       VARCHAR(100)  NOT NULL,
    description      VARCHAR(500),
    entry_date       DATE          NOT NULL,
    reversal_of      UUID REFERENCES project_cost_entries(id),
    idempotency_key  VARCHAR(120),
    posted_by        UUID,
    posted_by_name   VARCHAR(255),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_cost_entry_type   CHECK (entry_type IN ('COMMITMENT','ACTUAL')),
    CONSTRAINT ck_cost_entry_amount CHECK (amount <> 0)
);
-- Same key twice = same posting: this is what stops duplicate financial postings.
CREATE UNIQUE INDEX IF NOT EXISTS uq_cost_entry_idempotency
    ON project_cost_entries (tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
-- A given entry can be reversed at most once.
CREATE UNIQUE INDEX IF NOT EXISTS uq_cost_entry_reversal
    ON project_cost_entries (reversal_of) WHERE reversal_of IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_cost_entries_project ON project_cost_entries (tenant_id, project_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_cost_entries_line    ON project_cost_entries (budget_line_id);

CREATE TABLE IF NOT EXISTS project_budget_revisions (
    id               UUID PRIMARY KEY,
    tenant_id        UUID          NOT NULL,
    project_id       UUID          NOT NULL REFERENCES projects(id),
    budget_line_id   UUID          NOT NULL REFERENCES project_budget_lines(id),
    revision_type    VARCHAR(20)   NOT NULL,           -- REVISION | ADDITION | TRANSFER_OUT | TRANSFER_IN
    old_amount       NUMERIC(15,2) NOT NULL,
    new_amount       NUMERIC(15,2) NOT NULL,
    reason           TEXT,
    transfer_id      UUID,                             -- links the two halves of a transfer
    revised_by       UUID,
    revised_by_name  VARCHAR(255),
    revised_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_budget_revisions_project ON project_budget_revisions (tenant_id, project_id, revised_at DESC);

-- ── BACKFILL ────────────────────────────────────────────────────────────────
-- 1) Opening balances: the ledger becomes the source of truth, so existing line totals must
--    exist as entries or the first recalculation would zero them.
INSERT INTO project_cost_entries
    (id, tenant_id, project_id, budget_line_id, entry_type, amount, source_type, source_ref,
     description, entry_date, idempotency_key, posted_by_name, created_at)
SELECT gen_random_uuid(), l.tenant_id, l.project_id, l.id, 'COMMITMENT', l.committed_amount,
       'OPENING', l.id::text, 'Opening balance migrated from budget line', CURRENT_DATE,
       'OPENING:COMMITMENT:' || l.id::text, 'migration', now()
FROM project_budget_lines l
WHERE l.committed_amount <> 0
ON CONFLICT (tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING;

INSERT INTO project_cost_entries
    (id, tenant_id, project_id, budget_line_id, entry_type, amount, source_type, source_ref,
     description, entry_date, idempotency_key, posted_by_name, created_at)
SELECT gen_random_uuid(), l.tenant_id, l.project_id, l.id, 'ACTUAL', l.actual_amount,
       'OPENING', l.id::text, 'Opening balance migrated from budget line', CURRENT_DATE,
       'OPENING:ACTUAL:' || l.id::text, 'migration', now()
FROM project_budget_lines l
WHERE l.actual_amount <> 0
ON CONFLICT (tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING;

-- 2) Original budget for projects already past planning: best-effort = current amount.
--    Lines created by change-order approval are left NULL so they count as ADDITIONS, not original scope.
UPDATE project_budget_lines l
SET original_amount = l.budgeted_amount
FROM projects p
WHERE p.id = l.project_id
  AND p.status IN ('APPROVED','ACTIVE','ON_HOLD','COMPLETED','CLOSED')
  AND l.original_amount IS NULL
  AND l.description NOT LIKE 'Change Order:%';