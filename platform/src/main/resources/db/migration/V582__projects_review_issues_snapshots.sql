-- Patch 0570: task review status, issues register and action items, monthly portfolio snapshots.

-- ── Tasks: IN_REVIEW between In progress and Completed ──────────────────────
-- Drops every CHECK on project_tasks that mentions "status" (only chk_task_status in V88), then re-adds it.
DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'project_tasks'::regclass AND contype = 'c'
               AND pg_get_constraintdef(oid) ILIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE project_tasks DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;
ALTER TABLE project_tasks ADD CONSTRAINT chk_task_status
    CHECK (status IN ('NOT_STARTED','IN_PROGRESS','IN_REVIEW','COMPLETED','BLOCKED','CANCELLED'));

-- ── Issues: something that has happened and needs resolving ─────────────────
CREATE TABLE IF NOT EXISTS project_issues (
    id              UUID PRIMARY KEY,
    tenant_id       UUID          NOT NULL,
    project_id      UUID          NOT NULL REFERENCES projects(id),
    issue_number    VARCHAR(20)   NOT NULL,
    title           VARCHAR(300)  NOT NULL,
    description     TEXT,
    priority        VARCHAR(10)   NOT NULL DEFAULT 'MEDIUM',
    status          VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    risk_id         UUID          REFERENCES project_risks(id),        -- the risk it came from, if any
    owner_id        UUID,
    owner_name      VARCHAR(200),
    due_date        DATE,
    raised_by       UUID,
    raised_by_name  VARCHAR(200),
    resolution      TEXT,
    resolved_at     TIMESTAMPTZ,
    resolved_by_name VARCHAR(200),
    closed_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_issue_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT chk_issue_status   CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED','CLOSED')),
    -- resolved and closed issues say how they were resolved
    CONSTRAINT chk_issue_resolution CHECK (status IN ('OPEN','IN_PROGRESS') OR (resolution IS NOT NULL AND resolved_at IS NOT NULL)),
    CONSTRAINT uq_issue_number UNIQUE (tenant_id, project_id, issue_number)
);
CREATE INDEX IF NOT EXISTS idx_project_issues_project ON project_issues (tenant_id, project_id, status);

-- ── Action items: who does what by when, against a risk or an issue ─────────
CREATE TABLE IF NOT EXISTS project_action_items (
    id              UUID PRIMARY KEY,
    tenant_id       UUID          NOT NULL,
    project_id      UUID          NOT NULL REFERENCES projects(id),
    parent_type     VARCHAR(10)   NOT NULL,
    parent_id       UUID          NOT NULL,
    title           VARCHAR(300)  NOT NULL,
    owner_id        UUID,
    owner_name      VARCHAR(200),
    due_date        DATE,
    status          VARCHAR(12)   NOT NULL DEFAULT 'OPEN',
    done_at         TIMESTAMPTZ,
    done_by_name    VARCHAR(200),
    created_by      UUID,
    created_by_name VARCHAR(200),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_action_parent CHECK (parent_type IN ('RISK','ISSUE')),
    CONSTRAINT chk_action_status CHECK (status IN ('OPEN','DONE','CANCELLED')),
    CONSTRAINT chk_action_done   CHECK (status <> 'DONE' OR done_at IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_project_actions_project ON project_action_items (tenant_id, project_id, status);
CREATE INDEX IF NOT EXISTS idx_project_actions_parent  ON project_action_items (parent_type, parent_id);

-- ── Monthly snapshot per project, for dashboard trends and month-on-month deltas ──
-- One row per project per month, rewritten nightly during the month, so the last write of a month is its closing figure.
CREATE TABLE IF NOT EXISTS project_monthly_snapshots (
    tenant_id        UUID          NOT NULL,
    project_id       UUID          NOT NULL REFERENCES projects(id),
    month            DATE          NOT NULL,                 -- first day of the month
    status           VARCHAR(20)   NOT NULL,
    health           VARCHAR(10)   NOT NULL,
    overdue          BOOLEAN       NOT NULL,
    budget_total     NUMERIC(15,2) NOT NULL DEFAULT 0,
    budget_spent     NUMERIC(15,2) NOT NULL DEFAULT 0,
    budget_committed NUMERIC(15,2) NOT NULL DEFAULT 0,
    completion_pct   NUMERIC(5,2)  NOT NULL DEFAULT 0,
    taken_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, month),
    CONSTRAINT chk_snapshot_month CHECK (month = date_trunc('month', month)::date)
);
CREATE INDEX IF NOT EXISTS idx_project_snapshots_tenant ON project_monthly_snapshots (tenant_id, month);
