-- V37__tasks_module.sql
-- Tasks module — Kanban boards, task assignment, due dates, cross-module links.
-- WHY cross-module links? Tasks can be attached to any entity in the platform:
-- a quote, invoice, customer, lease, employee, creative job, AP bill, etc.
-- We use a soft reference (entity_type + entity_id) to avoid FK constraints
-- across module boundaries. Frontend resolves the link to show context.

-- ── Boards ────────────────────────────────────────────────────────────────────
CREATE TABLE task_boards (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    color       VARCHAR(20),    -- hex color for UI
    is_default  BOOLEAN     NOT NULL DEFAULT false,
    archived    BOOLEAN     NOT NULL DEFAULT false,
    created_by  UUID        REFERENCES users(id),
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_task_boards PRIMARY KEY (id)
);

CREATE INDEX idx_task_boards_tenant ON task_boards(tenant_id);

-- ── Columns (stages on a board) ───────────────────────────────────────────────
-- WHY tenant-defined columns? Different businesses have different workflows.
-- Default: Todo, In Progress, Done. But a dev team might want:
-- Backlog, Todo, In Progress, In Review, Done.
CREATE TABLE task_columns (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    board_id    UUID        NOT NULL REFERENCES task_boards(id) ON DELETE CASCADE,
    tenant_id   UUID        NOT NULL,
    name        VARCHAR(100) NOT NULL,
    color       VARCHAR(20),
    sort_order  INT         NOT NULL DEFAULT 0,
    -- WHY is_done_column? Tasks in this column are considered complete.
    -- Allows flexible column naming while still tracking completion.
    is_done_column BOOLEAN  NOT NULL DEFAULT false,

    CONSTRAINT pk_task_columns PRIMARY KEY (id)
);

CREATE INDEX idx_task_columns_board ON task_columns(board_id);

-- ── Tasks ─────────────────────────────────────────────────────────────────────
CREATE TABLE tasks (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    board_id        UUID        NOT NULL REFERENCES task_boards(id) ON DELETE CASCADE,
    column_id       UUID        NOT NULL REFERENCES task_columns(id),
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    priority        VARCHAR(10)  NOT NULL DEFAULT 'NORMAL'
        CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
    status          VARCHAR(20)  NOT NULL DEFAULT 'TODO'
        CHECK (status IN ('TODO','IN_PROGRESS','IN_REVIEW','DONE','CANCELLED')),
    assignee_id     UUID        REFERENCES users(id) ON DELETE SET NULL,
    due_date        DATE,
    estimated_hours NUMERIC(6,2),
    sort_order      INT         NOT NULL DEFAULT 0,

    -- Cross-module soft link
    -- e.g. linked_entity_type='QUOTE', linked_entity_id='uuid-of-quote'
    -- Supported types: QUOTE, INVOICE, CUSTOMER, LEASE, EMPLOYEE,
    --                  CREATIVE_JOB, AP_BILL, TICKET, PROPERTY
    linked_entity_type  VARCHAR(30),
    linked_entity_id    UUID,

    tags            TEXT[],
    created_by      UUID        REFERENCES users(id),
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMP,
    deleted_at      TIMESTAMP,
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_tasks PRIMARY KEY (id)
);

CREATE INDEX idx_tasks_tenant     ON tasks(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_tasks_board      ON tasks(board_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_tasks_column     ON tasks(column_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_tasks_assignee   ON tasks(assignee_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_tasks_due_date   ON tasks(tenant_id, due_date) WHERE deleted_at IS NULL;
CREATE INDEX idx_tasks_linked     ON tasks(linked_entity_type, linked_entity_id) WHERE linked_entity_id IS NOT NULL;

-- ── Task comments ─────────────────────────────────────────────────────────────
CREATE TABLE task_comments (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    task_id     UUID        NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    tenant_id   UUID        NOT NULL,
    author_id   UUID        REFERENCES users(id),
    author_name VARCHAR(255) NOT NULL,
    body        TEXT        NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_task_comments PRIMARY KEY (id)
);

CREATE INDEX idx_task_comments_task ON task_comments(task_id);

-- ── Task time logs ────────────────────────────────────────────────────────────
CREATE TABLE task_time_logs (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    task_id     UUID        NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    tenant_id   UUID        NOT NULL,
    user_id     UUID        REFERENCES users(id),
    user_name   VARCHAR(255) NOT NULL,
    hours       NUMERIC(6,2) NOT NULL,
    description TEXT,
    logged_date DATE        NOT NULL DEFAULT CURRENT_DATE,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_task_time_logs PRIMARY KEY (id)
);

CREATE INDEX idx_task_time_logs_task ON task_time_logs(task_id);

-- ── Module catalogue ──────────────────────────────────────────────────────────
INSERT INTO module_catalogue (key, name, description, monthly_price, icon, category, sort_order)
VALUES (
    'tasks',
    'Task Management',
    'Kanban boards, task assignment, due dates, time tracking, cross-module links to quotes, invoices, customers and more.',
    149.00, 'check-square', 'OPERATIONS', 95
) ON CONFLICT (key) DO NOTHING;

INSERT INTO tenant_modules (tenant_id, module_key, status, trial_ends_at)
SELECT t.id, 'tasks', 'TRIAL', NOW() + INTERVAL '60 days'
FROM tenants t WHERE t.slug = 'zeta-earthmoving'
ON CONFLICT (tenant_id, module_key) DO NOTHING;

-- ── Permissions ───────────────────────────────────────────────────────────────
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'TASKS_READ',   'View tasks and boards'),
    (gen_random_uuid(), 'TASKS_MANAGE', 'Create, assign and manage tasks'),
    (gen_random_uuid(), 'TASKS_ADMIN',  'Manage boards and columns')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('TASKS_READ', 'TASKS_MANAGE', 'TASKS_ADMIN')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- ── Seed default board for zeta-earthmoving ───────────────────────────────────
-- WHY seed here? Every tenant should have a default board on first use.
-- The application seeds it on first login, but we seed for the test tenant.
DO $$
DECLARE
    v_tenant_id UUID;
    v_board_id  UUID;
BEGIN
    SELECT id INTO v_tenant_id FROM tenants WHERE slug = 'zeta-earthmoving';
    IF v_tenant_id IS NOT NULL THEN
        v_board_id := gen_random_uuid();
        INSERT INTO task_boards (id, tenant_id, name, description, is_default, created_at, updated_at)
        VALUES (v_board_id, v_tenant_id, 'Main Board', 'Default task board', true, NOW(), NOW());

        INSERT INTO task_columns (id, board_id, tenant_id, name, color, sort_order, is_done_column)
        VALUES
            (gen_random_uuid(), v_board_id, v_tenant_id, 'To Do',       '#94A3B8', 0, false),
            (gen_random_uuid(), v_board_id, v_tenant_id, 'In Progress',  '#3B82F6', 1, false),
            (gen_random_uuid(), v_board_id, v_tenant_id, 'In Review',    '#F59E0B', 2, false),
            (gen_random_uuid(), v_board_id, v_tenant_id, 'Done',         '#10B981', 3, true);
    END IF;
END $$;
