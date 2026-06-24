-- V88 — Project Management module
-- Projects, phases, tasks, resources, budgets, risks, documents, site diary, snags
-- Package: za.co.handyflow.platform.projects

-- ── Projects ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS projects (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    project_number  VARCHAR(20)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    project_type    VARCHAR(30)  NOT NULL DEFAULT 'GENERAL',
                                 -- CONSTRUCTION | EARTHMOVING | SECURITY | EVENT | IT | GENERAL
    status          VARCHAR(20)  NOT NULL DEFAULT 'PLANNING',
                                 -- PLANNING | ACTIVE | ON_HOLD | COMPLETED | CANCELLED
    health          VARCHAR(10)  NOT NULL DEFAULT 'GREEN',
                                 -- GREEN | AMBER | RED
    client_id       UUID,        -- references CRM customer
    client_name     VARCHAR(200),
    site_address    TEXT,
    start_date      DATE,
    end_date        DATE,
    baseline_start  DATE,        -- original planned start (for variance)
    baseline_end    DATE,        -- original planned end
    -- Budget
    budget_total    NUMERIC(15,2) NOT NULL DEFAULT 0,
    budget_spent    NUMERIC(15,2) NOT NULL DEFAULT 0,
    budget_committed NUMERIC(15,2) NOT NULL DEFAULT 0, -- approved POs in progress
    -- Contract
    contract_value  NUMERIC(15,2),
    contract_ref    VARCHAR(100),
    contract_type   VARCHAR(30), -- FIXED_PRICE | TIME_AND_MATERIAL | COST_PLUS
    retention_pct   NUMERIC(5,2) DEFAULT 0,
    -- SA compliance
    cidb_grade      VARCHAR(10),
    nhbrc_number    VARCHAR(50),
    -- Meta
    project_manager_id   UUID,   -- references users
    project_manager_name VARCHAR(200),
    client_portal_token  VARCHAR(100) UNIQUE, -- public portal access
    notes           TEXT,
    created_by      UUID,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMP,
    cancelled_at    TIMESTAMP,

    CONSTRAINT chk_project_type   CHECK (project_type IN ('CONSTRUCTION','EARTHMOVING','SECURITY','EVENT','IT','GENERAL')),
    CONSTRAINT chk_project_status CHECK (status IN ('PLANNING','ACTIVE','ON_HOLD','COMPLETED','CANCELLED')),
    CONSTRAINT chk_project_health CHECK (health IN ('GREEN','AMBER','RED')),
    CONSTRAINT uq_project_number  UNIQUE (tenant_id, project_number)
);

CREATE INDEX IF NOT EXISTS idx_projects_tenant    ON projects(tenant_id);
CREATE INDEX IF NOT EXISTS idx_projects_status    ON projects(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_projects_client    ON projects(tenant_id, client_id);
CREATE INDEX IF NOT EXISTS idx_projects_manager   ON projects(tenant_id, project_manager_id);
CREATE INDEX IF NOT EXISTS idx_projects_portal    ON projects(client_portal_token);

-- ── Project Phases ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS project_phases (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenants(id),
    project_id  UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    status      VARCHAR(20)  NOT NULL DEFAULT 'NOT_STARTED',
    start_date  DATE,
    end_date    DATE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_phase_status CHECK (status IN ('NOT_STARTED','IN_PROGRESS','COMPLETED','SKIPPED'))
);
CREATE INDEX IF NOT EXISTS idx_phases_project ON project_phases(project_id);

-- ── Project Tasks ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS project_tasks (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    project_id      UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    phase_id        UUID         REFERENCES project_phases(id),
    parent_task_id  UUID         REFERENCES project_tasks(id), -- for WBS hierarchy
    task_number     VARCHAR(20),
    title           VARCHAR(300) NOT NULL,
    description     TEXT,
    task_type       VARCHAR(20)  NOT NULL DEFAULT 'TASK',
                                 -- TASK | MILESTONE | SUMMARY
    status          VARCHAR(20)  NOT NULL DEFAULT 'NOT_STARTED',
                                 -- NOT_STARTED | IN_PROGRESS | COMPLETED | BLOCKED | CANCELLED
    priority        VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM',
                                 -- LOW | MEDIUM | HIGH | CRITICAL
    assignee_id     UUID,
    assignee_name   VARCHAR(200),
    planned_start   DATE,
    planned_end     DATE,
    actual_start    DATE,
    actual_end      DATE,
    duration_days   INTEGER,
    progress_pct    NUMERIC(5,2) NOT NULL DEFAULT 0,
    estimated_hours NUMERIC(8,2),
    actual_hours    NUMERIC(8,2) NOT NULL DEFAULT 0,
    is_critical     BOOLEAN      NOT NULL DEFAULT false, -- on critical path
    is_milestone    BOOLEAN      NOT NULL DEFAULT false,
    -- Budget
    budget_amount   NUMERIC(15,2),
    actual_cost     NUMERIC(15,2) NOT NULL DEFAULT 0,
    -- OHSA / compliance
    requires_inspection BOOLEAN  NOT NULL DEFAULT false,
    inspection_passed   BOOLEAN,
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    notes           TEXT,
    created_by      UUID,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_task_type     CHECK (task_type IN ('TASK','MILESTONE','SUMMARY')),
    CONSTRAINT chk_task_status   CHECK (status IN ('NOT_STARTED','IN_PROGRESS','COMPLETED','BLOCKED','CANCELLED')),
    CONSTRAINT chk_task_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL'))
);
CREATE INDEX IF NOT EXISTS idx_tasks_project  ON project_tasks(project_id);
CREATE INDEX IF NOT EXISTS idx_tasks_phase    ON project_tasks(phase_id);
CREATE INDEX IF NOT EXISTS idx_tasks_assignee ON project_tasks(tenant_id, assignee_id);
CREATE INDEX IF NOT EXISTS idx_tasks_status   ON project_tasks(tenant_id, status);

-- ── Task Dependencies ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS task_dependencies (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    predecessor_id  UUID NOT NULL REFERENCES project_tasks(id) ON DELETE CASCADE,
    successor_id    UUID NOT NULL REFERENCES project_tasks(id) ON DELETE CASCADE,
    dependency_type VARCHAR(3)  NOT NULL DEFAULT 'FS',
                                -- FS (Finish-Start) | SS | FF | SF
    lag_days        INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT uq_task_dep UNIQUE (predecessor_id, successor_id),
    CONSTRAINT chk_dep_type CHECK (dependency_type IN ('FS','SS','FF','SF'))
);

-- ── Project Resources ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS project_resources (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    project_id      UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    task_id         UUID         REFERENCES project_tasks(id),
    resource_type   VARCHAR(20)  NOT NULL DEFAULT 'HUMAN',
                                 -- HUMAN | EQUIPMENT | VEHICLE | SUBCONTRACTOR
    resource_id     UUID,        -- user_id | asset_id | vehicle_id
    resource_name   VARCHAR(200) NOT NULL,
    role            VARCHAR(100),
    allocation_pct  NUMERIC(5,2) NOT NULL DEFAULT 100, -- % of time allocated
    start_date      DATE,
    end_date        DATE,
    hourly_rate     NUMERIC(10,2),
    daily_rate      NUMERIC(10,2),
    planned_hours   NUMERIC(8,2),
    actual_hours    NUMERIC(8,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_resource_type CHECK (resource_type IN ('HUMAN','EQUIPMENT','VEHICLE','SUBCONTRACTOR'))
);
CREATE INDEX IF NOT EXISTS idx_resources_project  ON project_resources(project_id);
CREATE INDEX IF NOT EXISTS idx_resources_resource ON project_resources(tenant_id, resource_id);

-- ── Time Entries ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS time_entries (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    project_id      UUID         NOT NULL REFERENCES projects(id),
    task_id         UUID         REFERENCES project_tasks(id),
    user_id         UUID         NOT NULL,
    user_name       VARCHAR(200) NOT NULL,
    entry_date      DATE         NOT NULL,
    hours           NUMERIC(5,2) NOT NULL,
    description     TEXT,
    -- GPS stamp for field accountability
    latitude        NUMERIC(10,7),
    longitude       NUMERIC(10,7),
    -- Approval
    status          VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED',
                                 -- SUBMITTED | APPROVED | REJECTED
    approved_by     UUID,
    approved_at     TIMESTAMP,
    -- Payroll integration flag
    payroll_run_id  UUID,        -- set when included in payroll
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_time_status CHECK (status IN ('SUBMITTED','APPROVED','REJECTED'))
);
CREATE INDEX IF NOT EXISTS idx_time_project ON time_entries(project_id);
CREATE INDEX IF NOT EXISTS idx_time_task    ON time_entries(task_id);
CREATE INDEX IF NOT EXISTS idx_time_user    ON time_entries(tenant_id, user_id, entry_date);
CREATE INDEX IF NOT EXISTS idx_time_payroll ON time_entries(payroll_run_id);

-- ── Budget Lines ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS project_budget_lines (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    project_id      UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    phase_id        UUID         REFERENCES project_phases(id),
    category        VARCHAR(50)  NOT NULL,
                                 -- LABOUR | MATERIALS | SUBCONTRACT | EQUIPMENT | OVERHEAD | CONTINGENCY
    description     VARCHAR(300) NOT NULL,
    budgeted_amount NUMERIC(15,2) NOT NULL,
    committed_amount NUMERIC(15,2) NOT NULL DEFAULT 0, -- linked PO value
    actual_amount   NUMERIC(15,2) NOT NULL DEFAULT 0,
    -- SA-specific
    is_provisional  BOOLEAN      NOT NULL DEFAULT false, -- provisional sum
    is_prime_cost   BOOLEAN      NOT NULL DEFAULT false, -- PC item
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_budget_category CHECK (category IN ('LABOUR','MATERIALS','SUBCONTRACT','EQUIPMENT','OVERHEAD','CONTINGENCY'))
);
CREATE INDEX IF NOT EXISTS idx_budget_project ON project_budget_lines(project_id);

-- ── Change Orders ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS change_orders (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    project_id      UUID         NOT NULL REFERENCES projects(id),
    change_number   VARCHAR(20)  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    reason          TEXT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
                                 -- DRAFT | SUBMITTED | APPROVED | REJECTED
    -- Impact
    cost_impact     NUMERIC(15,2) NOT NULL DEFAULT 0, -- positive = cost increase
    schedule_impact INTEGER       NOT NULL DEFAULT 0, -- days
    -- Approval
    submitted_by    UUID,
    submitted_at    TIMESTAMP,
    approved_by     UUID,
    approved_by_name VARCHAR(200),
    approved_at     TIMESTAMP,
    client_approved_at TIMESTAMP,
    rejection_reason TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_co_status CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED')),
    CONSTRAINT uq_change_number UNIQUE (tenant_id, project_id, change_number)
);
CREATE INDEX IF NOT EXISTS idx_change_orders_project ON change_orders(project_id);

-- ── Project Risks ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS project_risks (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    project_id      UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    risk_number     VARCHAR(20),
    title           VARCHAR(300) NOT NULL,
    description     TEXT,
    category        VARCHAR(50), -- SAFETY | FINANCIAL | SCHEDULE | TECHNICAL | LEGAL | ENVIRONMENTAL
    probability     INTEGER      NOT NULL DEFAULT 3 CHECK (probability BETWEEN 1 AND 5),
    impact          INTEGER      NOT NULL DEFAULT 3 CHECK (impact BETWEEN 1 AND 5),
    risk_score      INTEGER      GENERATED ALWAYS AS (probability * impact) STORED,
    -- AMBER ≥ 9, RED ≥ 15
    rating          VARCHAR(10)  NOT NULL DEFAULT 'AMBER',
                                 -- GREEN | AMBER | RED
    status          VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
                                 -- OPEN | MITIGATED | CLOSED | ACCEPTED
    mitigation      TEXT,
    owner_id        UUID,
    owner_name      VARCHAR(200),
    review_date     DATE,
    -- OHSA / SA compliance
    is_ohsa         BOOLEAN      NOT NULL DEFAULT false,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_risk_rating CHECK (rating IN ('GREEN','AMBER','RED')),
    CONSTRAINT chk_risk_status CHECK (status IN ('OPEN','MITIGATED','CLOSED','ACCEPTED'))
);
CREATE INDEX IF NOT EXISTS idx_risks_project ON project_risks(project_id);
CREATE INDEX IF NOT EXISTS idx_risks_rating  ON project_risks(tenant_id, rating);

-- ── Project Documents ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS project_documents (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    project_id      UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    document_type   VARCHAR(30)  NOT NULL DEFAULT 'GENERAL',
                                 -- DRAWING | RFI | SUBMITTAL | CONTRACT | REPORT | PHOTO | GENERAL
    title           VARCHAR(300) NOT NULL,
    revision        VARCHAR(20),
    file_url        TEXT,
    file_name       VARCHAR(300),
    file_size_kb    INTEGER,
    status          VARCHAR(20)  NOT NULL DEFAULT 'CURRENT',
                                 -- DRAFT | FOR_REVIEW | APPROVED | SUPERSEDED | CURRENT
    description     TEXT,
    uploaded_by     UUID,
    uploaded_by_name VARCHAR(200),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_doc_type   CHECK (document_type IN ('DRAWING','RFI','SUBMITTAL','CONTRACT','REPORT','PHOTO','GENERAL')),
    CONSTRAINT chk_doc_status CHECK (status IN ('DRAFT','FOR_REVIEW','APPROVED','SUPERSEDED','CURRENT'))
);
CREATE INDEX IF NOT EXISTS idx_docs_project ON project_documents(project_id);
CREATE INDEX IF NOT EXISTS idx_docs_type    ON project_documents(project_id, document_type);

-- ── Site Diaries ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS site_diaries (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    project_id      UUID         NOT NULL REFERENCES projects(id),
    diary_date      DATE         NOT NULL,
    weather         VARCHAR(50), -- CLEAR | CLOUDY | RAIN | STORM | WIND
    temp_celsius    NUMERIC(4,1),
    -- Attendance
    workers_present INTEGER      NOT NULL DEFAULT 0,
    workers_planned INTEGER,
    -- Work done
    work_description TEXT,
    progress_notes  TEXT,
    issues          TEXT,
    -- Visitors
    visitor_names   TEXT,
    -- Safety
    incidents       TEXT,
    toolbox_topic   TEXT,
    -- Equipment on site (JSON array of equipment names)
    equipment_notes TEXT,
    -- Submitted by
    submitted_by    UUID,
    submitted_by_name VARCHAR(200),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_diary_date UNIQUE (project_id, diary_date)
);
CREATE INDEX IF NOT EXISTS idx_diary_project ON site_diaries(project_id, diary_date DESC);

-- ── Snag Items ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS snag_items (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    project_id      UUID         NOT NULL REFERENCES projects(id),
    task_id         UUID         REFERENCES project_tasks(id),
    snag_number     VARCHAR(20)  NOT NULL,
    title           VARCHAR(300) NOT NULL,
    description     TEXT,
    location        VARCHAR(200),
    severity        VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM',
                                 -- LOW | MEDIUM | HIGH | CRITICAL
    status          VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
                                 -- OPEN | IN_PROGRESS | RESOLVED | REJECTED
    assigned_to     UUID,
    assigned_to_name VARCHAR(200),
    due_date        DATE,
    photo_urls      TEXT[],      -- array of photo URLs
    resolved_at     TIMESTAMP,
    resolved_by     UUID,
    created_by      UUID,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_snag_severity CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT chk_snag_status   CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED','REJECTED')),
    CONSTRAINT uq_snag_number    UNIQUE (project_id, snag_number)
);
CREATE INDEX IF NOT EXISTS idx_snags_project ON snag_items(project_id);
CREATE INDEX IF NOT EXISTS idx_snags_status  ON snag_items(project_id, status);

-- ── PM Permissions ────────────────────────────────────────────────────────────
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'PM_READ',     'View projects, tasks, schedules and reports'),
    (gen_random_uuid(), 'PM_WRITE',    'Create and update projects, tasks, time entries'),
    (gen_random_uuid(), 'PM_ADMIN',    'Manage project settings, resources, budgets'),
    (gen_random_uuid(), 'PM_APPROVE',  'Approve time entries, change orders, risk actions')
ON CONFLICT (name) DO NOTHING;

-- OWNER: all PM permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'OWNER' AND p.name LIKE 'PM_%'
ON CONFLICT DO NOTHING;

-- ADMIN: read, write, approve (no admin)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN' AND p.name IN ('PM_READ','PM_WRITE','PM_APPROVE')
ON CONFLICT DO NOTHING;

-- EMPLOYEE: read + write (log time, update tasks)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'EMPLOYEE' AND p.name IN ('PM_READ','PM_WRITE')
ON CONFLICT DO NOTHING;