-- HR slice 3: employee master and security.
--
-- 1. Separate permissions for personal data and for pay, so that being able to read the staff list no longer means
--    being able to read ID numbers, salaries and bank details. Mapped from what roles hold today; the roles that lose
--    something are written to hr_employee_access_review.
-- 2. Manager scope: a manager sees their own team. Needs a link from a user login to an employee.
-- 3. Termination keeps its reason and who recorded it.
-- 4. An audit trail per employee, and approved changes to pay and bank details with who asked and who decided.

-- ── 1. Permissions ──────────────────────────────────────────────────────────────────────────────
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'HR_SENSITIVE_READ',      'View ID number, tax number, date of birth, gender, race and emergency contacts'),
    (gen_random_uuid(), 'HR_COMPENSATION_READ',   'View salary, allowances and masked bank details'),
    (gen_random_uuid(), 'HR_COMPENSATION_MANAGE', 'Ask for changes to salary, allowances and bank details'),
    (gen_random_uuid(), 'HR_COMPENSATION_APPROVE','Approve or reject changes to salary, allowances and bank details'),
    (gen_random_uuid(), 'HR_TEAM_READ',           'View the working details of your own team')
ON CONFLICT (name) DO NOTHING;

CREATE TABLE hr_employee_access_review (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL REFERENCES tenants(id),
    role_id        UUID         NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    role_name      VARCHAR(100) NOT NULL,
    users_in_role  INT          NOT NULL,
    legacy_held    VARCHAR(400) NOT NULL,
    granted        VARCHAR(400) NOT NULL,
    needs_review   BOOLEAN      NOT NULL,
    note           VARCHAR(500) NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_hr_employee_access_review_tenant ON hr_employee_access_review (tenant_id);

-- Written before any grant, from what each role holds today.
INSERT INTO hr_employee_access_review (tenant_id, role_id, role_name, users_in_role, legacy_held, granted, needs_review, note)
SELECT t.tenant_id, t.role_id, t.role_name, t.users_in_role, t.held,
       concat_ws(', ',
           CASE WHEN t.has_manage THEN 'HR_SENSITIVE_READ, HR_COMPENSATION_READ, HR_COMPENSATION_MANAGE' END,
           CASE WHEN NOT t.has_manage AND t.has_payroll THEN 'HR_COMPENSATION_READ' END,
           CASE WHEN t.is_admin THEN 'HR_COMPENSATION_APPROVE' END),
       (t.has_read AND NOT t.has_manage),
       CASE
         WHEN t.is_admin THEN 'Administrator role: can also approve changes to pay and bank details, so there is always an approver. Remove it if someone else should decide.'
         WHEN t.has_manage THEN 'Could manage employees: keeps seeing personal data and pay, and can ask for pay and bank changes. Approving was never granted.'
         WHEN t.has_read THEN 'Could read employees, which included ID numbers and salaries. It now sees names, jobs and contact details only. Grant HR_SENSITIVE_READ or HR_COMPENSATION_READ if this role needs more.'
         ELSE 'Payroll role: now also sees salary and allowances on the employee record.'
       END
FROM (
    SELECT r.tenant_id, r.id AS role_id, r.name AS role_name,
           (SELECT COUNT(*) FROM user_roles ur WHERE ur.role_id = r.id)::int AS users_in_role,
           string_agg(DISTINCT p.name, ', ' ORDER BY p.name) AS held,
           bool_or(p.name = 'HR_MANAGE') AS has_manage,
           bool_or(p.name = 'HR_READ') AS has_read,
           bool_or(p.name IN ('PAYROLL_PREPARE', 'PAYROLL_RUN', 'PAYROLL_APPROVE')) AS has_payroll,
           (r.name = 'ADMIN') AS is_admin
    FROM roles r
    JOIN role_permissions rp ON rp.role_id = r.id
    JOIN permissions p ON p.id = rp.permission_id
    WHERE p.name IN ('HR_READ', 'HR_MANAGE', 'PAYROLL_PREPARE', 'PAYROLL_RUN', 'PAYROLL_APPROVE')
    GROUP BY r.tenant_id, r.id, r.name
) t;

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, p.id
FROM role_permissions rp
JOIN permissions m ON m.id = rp.permission_id AND m.name = 'HR_MANAGE'
CROSS JOIN permissions p
WHERE p.name IN ('HR_SENSITIVE_READ', 'HR_COMPENSATION_READ', 'HR_COMPENSATION_MANAGE')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, p.id
FROM role_permissions rp
JOIN permissions m ON m.id = rp.permission_id AND m.name IN ('PAYROLL_PREPARE', 'PAYROLL_RUN', 'PAYROLL_APPROVE')
CROSS JOIN permissions p
WHERE p.name = 'HR_COMPENSATION_READ'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN' AND p.name = 'HR_COMPENSATION_APPROVE'
ON CONFLICT DO NOTHING;

-- HR_TEAM_READ is granted to no role: it is given on purpose to the managers who should have it.

-- ── 2 and 3. Employee columns ───────────────────────────────────────────────────────────────────
ALTER TABLE hr_employees
    ADD COLUMN user_id             UUID,
    ADD COLUMN termination_reason  VARCHAR(200),
    ADD COLUMN terminated_by       UUID,
    ADD COLUMN terminated_at       TIMESTAMP;

CREATE UNIQUE INDEX uq_hr_employees_user ON hr_employees (tenant_id, user_id) WHERE user_id IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX idx_hr_employees_id_number ON hr_employees (tenant_id, id_number) WHERE id_number IS NOT NULL;
CREATE INDEX idx_hr_employees_manager ON hr_employees (tenant_id, manager_id) WHERE manager_id IS NOT NULL;

-- ── 4. Audit and change control ─────────────────────────────────────────────────────────────────
CREATE TABLE hr_employee_audit (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL REFERENCES tenants(id),
    employee_id  UUID         NOT NULL REFERENCES hr_employees(id),
    action       VARCHAR(40)  NOT NULL,
    detail       VARCHAR(500),
    actor_id     UUID,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_hr_employee_audit_employee ON hr_employee_audit (tenant_id, employee_id, created_at DESC);

CREATE TABLE hr_employee_changes (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL REFERENCES tenants(id),
    employee_id    UUID         NOT NULL REFERENCES hr_employees(id),
    change_type    VARCHAR(20)  NOT NULL CHECK (change_type IN ('COMPENSATION', 'BANK_DETAILS')),
    old_values     JSONB        NOT NULL,
    new_values     JSONB        NOT NULL,
    effective_from DATE         NOT NULL,
    reason         VARCHAR(500) NOT NULL,
    status         VARCHAR(12)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'APPLIED', 'REJECTED', 'CANCELLED')),
    requested_by   UUID,
    requested_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    decided_by     UUID,
    decided_at     TIMESTAMP,
    decision_note  VARCHAR(500),
    self_approved  BOOLEAN      NOT NULL DEFAULT FALSE,
    applied_at     TIMESTAMP
);
CREATE INDEX idx_hr_employee_changes_status ON hr_employee_changes (tenant_id, status);
CREATE INDEX idx_hr_employee_changes_employee ON hr_employee_changes (tenant_id, employee_id, requested_at DESC);
-- One open request per employee and kind, so two people cannot have competing pay changes waiting.
CREATE UNIQUE INDEX uq_hr_employee_changes_open ON hr_employee_changes (tenant_id, employee_id, change_type) WHERE status = 'PENDING';
