-- HR slice 2: payroll controls.
--
-- 1. Dedicated PAYROLL_* permissions, mapped from what payroll relied on before (decision 4: no blanket grant to everyone
--    who held USER_UPDATE, and a report of what was and was not mapped).
-- 2. Pay run workflow: PREPARED -> APPROVED -> FINALISED, with who did each step and a history of events.
-- 3. A tenant setting for small businesses where one person has to prepare and approve.
--
-- Legacy status COMPLETED is kept for runs made before approval existed.

-- ── 1. Permissions ──────────────────────────────────────────────────────────────────────────────
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'PAYROLL_PREPARE',              'Calculate and recalculate pay runs'),
    (gen_random_uuid(), 'PAYROLL_APPROVE',              'Approve or send back a calculated pay run'),
    (gen_random_uuid(), 'PAYROLL_FINALISE',             'Finalise and lock an approved pay run'),
    (gen_random_uuid(), 'PAYROLL_BANK_FILE_GENERATE',   'Generate the bank payment file for a finalised pay run'),
    (gen_random_uuid(), 'PAYROLL_STATUTORY_EXPORT',     'Export EMP201 and other statutory documents'),
    (gen_random_uuid(), 'PAYROLL_CONFIGURATION_ADMIN',  'Change payroll settings and see the payroll access review')
ON CONFLICT (name) DO NOTHING;

-- The review table is written before any grant, from the permissions roles hold today.
CREATE TABLE hr_payroll_access_review (
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
CREATE INDEX idx_hr_payroll_access_review_tenant ON hr_payroll_access_review (tenant_id);

INSERT INTO hr_payroll_access_review (tenant_id, role_id, role_name, users_in_role, legacy_held, granted, needs_review, note)
SELECT t.tenant_id, t.role_id, t.role_name, t.users_in_role, t.legacy_held,
       CASE WHEN t.has_run OR t.is_admin THEN 'PAYROLL_READ, PAYROLL_PREPARE, PAYROLL_STATUTORY_EXPORT' ELSE '' END
         || CASE WHEN t.is_admin THEN ', PAYROLL_APPROVE, PAYROLL_FINALISE, PAYROLL_CONFIGURATION_ADMIN' ELSE '' END,
       NOT (t.has_run OR t.is_admin),
       CASE
         WHEN t.is_admin THEN 'Administrator role: also given approve, finalise and configuration so payroll is never left without an approver. Remove them if someone else should approve.'
         WHEN t.has_run THEN 'Could process pay runs: now prepares and exports. Approving and finalising were never granted.'
         WHEN t.has_update THEN 'Held USER_UPDATE but no payroll permission. It can still prepare pay runs through USER_UPDATE until 2026-12-31 only. Grant PAYROLL_PREPARE if this role should keep payroll access.'
         ELSE 'Can view pay runs only. Nothing new was granted.'
       END
FROM (
    SELECT r.tenant_id, r.id AS role_id, r.name AS role_name,
           (SELECT COUNT(*) FROM user_roles ur WHERE ur.role_id = r.id)::int AS users_in_role,
           string_agg(DISTINCT p.name, ', ' ORDER BY p.name) AS legacy_held,
           bool_or(p.name = 'PAYROLL_RUN')  AS has_run,
           bool_or(p.name = 'USER_UPDATE')  AS has_update,
           (r.name = 'ADMIN')               AS is_admin
    FROM roles r
    JOIN role_permissions rp ON rp.role_id = r.id
    JOIN permissions p ON p.id = rp.permission_id
    WHERE p.name IN ('PAYROLL_READ', 'PAYROLL_RUN', 'USER_UPDATE')
    GROUP BY r.tenant_id, r.id, r.name
) t;

-- Roles that could process pay runs keep doing so.
INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, p.id
FROM role_permissions rp
JOIN permissions run ON run.id = rp.permission_id AND run.name = 'PAYROLL_RUN'
CROSS JOIN permissions p
WHERE p.name IN ('PAYROLL_READ', 'PAYROLL_PREPARE', 'PAYROLL_STATUTORY_EXPORT')
ON CONFLICT DO NOTHING;

-- The tenant administration role also approves, finalises and configures. Nobody else is given these.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN' AND p.name IN ('PAYROLL_APPROVE', 'PAYROLL_FINALISE', 'PAYROLL_CONFIGURATION_ADMIN')
ON CONFLICT DO NOTHING;

-- PAYROLL_BANK_FILE_GENERATE is granted to no role: there is no bank file yet.

-- ── 2. Workflow ──────────────────────────────────────────────────────────────────────────────────
ALTER TABLE hr_pay_runs DROP CONSTRAINT IF EXISTS hr_pay_runs_status_check;
ALTER TABLE hr_pay_runs ADD CONSTRAINT hr_pay_runs_status_check
    CHECK (status IN ('DRAFT', 'PROCESSING', 'PREPARED', 'APPROVED', 'FINALISED', 'COMPLETED', 'CANCELLED'));

ALTER TABLE hr_pay_runs
    ADD COLUMN prepared_by   UUID,
    ADD COLUMN prepared_at   TIMESTAMP,
    ADD COLUMN approved_by   UUID,
    ADD COLUMN approved_at   TIMESTAMP,
    ADD COLUMN finalised_by  UUID,
    ADD COLUMN finalised_at  TIMESTAMP;

CREATE TABLE hr_pay_run_events (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL REFERENCES tenants(id),
    pay_run_id   UUID         NOT NULL REFERENCES hr_pay_runs(id),
    action       VARCHAR(30)  NOT NULL,
    from_status  VARCHAR(20)  NOT NULL,
    to_status    VARCHAR(20)  NOT NULL,
    actor_id     UUID,
    note         VARCHAR(500),
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_hr_pay_run_events_run ON hr_pay_run_events (tenant_id, pay_run_id, created_at);

-- ── 3. Tenant setting ────────────────────────────────────────────────────────────────────────────
-- Off by default. A business where one person prepares and approves can switch it on; every self-approval is then
-- written to the run's history.
CREATE TABLE hr_payroll_settings (
    tenant_id            UUID PRIMARY KEY REFERENCES tenants(id),
    allow_self_approval  BOOLEAN   NOT NULL DEFAULT FALSE,
    updated_by           UUID,
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW()
);
