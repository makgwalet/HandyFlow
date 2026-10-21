-- HR slice 6: onboarding of new employees.
--
-- Recruitment itself stays in the Recruiter module; when it hires someone it creates the employee through HR, and HR starts the
-- onboarding for every new employee, however they were created. Existing employees are not given an onboarding.
--
-- 1. HR_ONBOARDING_MANAGE: change the task list, tick, waive and assign any task, finish or cancel an onboarding.
--    Given to roles that already hold HR_MANAGE. Anyone a task is assigned to can tick that task without it.
-- 2. The tenant's own task list (template), created from the standard list the first time it is needed.
-- 3. One onboarding per employee, with its tasks.

-- ── 1. Permission ────────────────────────────────────────────────────────────────────────────────
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'HR_ONBOARDING_MANAGE', 'Manage onboarding of new employees: task list, ticking, waiving, finishing')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, p.id
FROM role_permissions rp
JOIN permissions held ON held.id = rp.permission_id AND held.name = 'HR_MANAGE'
CROSS JOIN permissions p
WHERE p.name = 'HR_ONBOARDING_MANAGE'
ON CONFLICT DO NOTHING;

-- ── 2. Template ──────────────────────────────────────────────────────────────────────────────────
CREATE TABLE hr_onboarding_templates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    title           VARCHAR(200) NOT NULL,
    description     VARCHAR(1000),
    category        VARCHAR(20)  NOT NULL CHECK (category IN ('CONTRACT', 'DOCUMENTS', 'PAYROLL', 'COMPLIANCE', 'ACCESS', 'INDUCTION')),
    required        BOOLEAN      NOT NULL DEFAULT TRUE,
    due_offset_days INT          NOT NULL DEFAULT 0,
    due_basis       VARCHAR(20)  NOT NULL DEFAULT 'START' CHECK (due_basis IN ('START', 'PROBATION_END')),
    check_key       VARCHAR(30),
    sort_order      INT          NOT NULL DEFAULT 0,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_hr_onboarding_templates_tenant ON hr_onboarding_templates (tenant_id, active, sort_order);

-- ── 3. Onboardings and tasks ─────────────────────────────────────────────────────────────────────
CREATE TABLE hr_onboardings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL REFERENCES tenants(id),
    employee_id         UUID        NOT NULL REFERENCES hr_employees(id),
    start_date          DATE        NOT NULL,
    probation_end_date  DATE        NOT NULL,
    status              VARCHAR(12) NOT NULL DEFAULT 'IN_PROGRESS' CHECK (status IN ('IN_PROGRESS', 'COMPLETE', 'CANCELLED')),
    started_by          UUID,
    completed_by        UUID,
    completed_at        TIMESTAMP,
    cancel_reason       VARCHAR(500),
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    version             BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_hr_onboardings_employee ON hr_onboardings (tenant_id, employee_id);
CREATE INDEX idx_hr_onboardings_status ON hr_onboardings (tenant_id, status);

CREATE TABLE hr_onboarding_tasks (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL REFERENCES tenants(id),
    onboarding_id  UUID         NOT NULL REFERENCES hr_onboardings(id),
    title          VARCHAR(200) NOT NULL,
    description    VARCHAR(1000),
    category       VARCHAR(20)  NOT NULL CHECK (category IN ('CONTRACT', 'DOCUMENTS', 'PAYROLL', 'COMPLIANCE', 'ACCESS', 'INDUCTION')),
    required       BOOLEAN      NOT NULL DEFAULT TRUE,
    due_date       DATE,
    check_key      VARCHAR(30),
    assignee_id    UUID,
    status         VARCHAR(10)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'DONE', 'WAIVED')),
    note           VARCHAR(500),
    resolved_by    UUID,
    resolved_at    TIMESTAMP,
    sort_order     INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    version        BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_hr_onboarding_tasks_onboarding ON hr_onboarding_tasks (onboarding_id, sort_order);
CREATE INDEX idx_hr_onboarding_tasks_assignee ON hr_onboarding_tasks (tenant_id, assignee_id) WHERE status = 'OPEN';
