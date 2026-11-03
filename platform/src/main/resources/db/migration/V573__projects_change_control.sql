-- Chunk 5: change control — structured impacts, versioned revisions, baseline history.
-- Rename with your next Flyway version number. Requires PostgreSQL 13+ (gen_random_uuid(), jsonb).

-- ── Change orders: new lifecycle fields ─────────────────────────────────────
ALTER TABLE change_orders
    ADD COLUMN IF NOT EXISTS revision                    INT         NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS scope_impact                TEXT,
    ADD COLUMN IF NOT EXISTS risk_impact                 TEXT,
    ADD COLUMN IF NOT EXISTS procurement_impact          TEXT,
    ADD COLUMN IF NOT EXISTS rejected_at                 TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS withdrawn_at                TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS withdrawn_reason            TEXT,
    ADD COLUMN IF NOT EXISTS client_disputed_at          TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS client_dispute_reason       TEXT,
    ADD COLUMN IF NOT EXISTS client_dispute_resolved_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS client_dispute_resolution   TEXT,
    ADD COLUMN IF NOT EXISTS applied_at                  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS baseline_version            INT;

-- Allow the new WITHDRAWN status. Review first: drops every CHECK on change_orders mentioning "status".
DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'change_orders'::regclass AND contype = 'c'
               AND pg_get_constraintdef(oid) ILIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE change_orders DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;
ALTER TABLE change_orders ADD CONSTRAINT change_orders_status_check
    CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','WITHDRAWN'));

-- ── Structured impact: which budget lines, which tasks ──────────────────────
CREATE TABLE IF NOT EXISTS change_order_cost_lines (
    id               UUID PRIMARY KEY,
    tenant_id        UUID          NOT NULL,
    change_order_id  UUID          NOT NULL REFERENCES change_orders(id),
    budget_line_id   UUID          REFERENCES project_budget_lines(id),   -- NULL = a NEW line is created on approval
    category         VARCHAR(50),
    description      VARCHAR(300),
    amount           NUMERIC(15,2) NOT NULL,                              -- signed: negative reduces a line
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_co_cost_line_amount CHECK (amount <> 0)
);
CREATE INDEX IF NOT EXISTS idx_co_cost_lines_co ON change_order_cost_lines (change_order_id);

CREATE TABLE IF NOT EXISTS change_order_tasks (
    id               UUID PRIMARY KEY,
    tenant_id        UUID          NOT NULL,
    change_order_id  UUID          NOT NULL REFERENCES change_orders(id),
    task_id          UUID          NOT NULL REFERENCES project_tasks(id),
    delay_days       INT           NOT NULL DEFAULT 0,                    -- working days the task is extended by
    CONSTRAINT ck_co_task_delay CHECK (delay_days >= 0),
    CONSTRAINT uq_co_task UNIQUE (change_order_id, task_id)
);

-- ── Every submission is kept: versioned quotations / variation history ──────
CREATE TABLE IF NOT EXISTS change_order_revisions (
    id                UUID PRIMARY KEY,
    tenant_id         UUID          NOT NULL,
    change_order_id   UUID          NOT NULL REFERENCES change_orders(id),
    revision          INT           NOT NULL,
    title             VARCHAR(500)  NOT NULL,
    description       TEXT,
    reason            TEXT,
    cost_impact       NUMERIC(15,2) NOT NULL,
    schedule_impact   INT           NOT NULL,
    detail_json       TEXT,                                               -- cost lines + affected tasks as submitted
    submitted_by      UUID,
    submitted_by_name VARCHAR(255),
    submitted_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    outcome           VARCHAR(20),                                        -- APPROVED | REJECTED | WITHDRAWN
    outcome_reason    TEXT,
    decided_by        UUID,
    decided_at        TIMESTAMPTZ,
    CONSTRAINT uq_co_revision UNIQUE (change_order_id, revision)
);

-- ── Budget revisions can point at the change order that caused them ─────────
ALTER TABLE project_budget_revisions ADD COLUMN IF NOT EXISTS change_order_id UUID;

-- ── Versioned project baseline ──────────────────────────────────────────────
ALTER TABLE projects ADD COLUMN IF NOT EXISTS baseline_version INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS project_baseline_revisions (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL,
    project_id       UUID         NOT NULL REFERENCES projects(id),
    version          INT          NOT NULL,
    change_order_id  UUID,
    summary          VARCHAR(500) NOT NULL,
    detail_json      TEXT         NOT NULL,
    applied_by       UUID,
    applied_by_name  VARCHAR(255),
    applied_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_baseline_version UNIQUE (project_id, version)
);

-- Version 1 = the baseline as first locked. Best effort for projects already past planning.
UPDATE projects SET baseline_version = 1
WHERE baseline_version = 0 AND status IN ('APPROVED','ACTIVE','ON_HOLD','COMPLETED','CLOSED');

INSERT INTO project_baseline_revisions
    (id, tenant_id, project_id, version, change_order_id, summary, detail_json, applied_by_name, applied_at)
SELECT gen_random_uuid(), p.tenant_id, p.id, 1, NULL,
       'Initial baseline (reconstructed at migration)',
       jsonb_build_object('baselineStart', p.baseline_start, 'baselineEnd', p.baseline_end,
                          'note', 'Reconstructed from current baseline dates; the true original may differ')::text,
       'migration', now()
FROM projects p
WHERE p.baseline_version = 1
ON CONFLICT (project_id, version) DO NOTHING;

-- Existing APPROVED change orders were applied before versioning existed.
UPDATE change_orders SET applied_at = approved_at
WHERE status = 'APPROVED' AND applied_at IS NULL;