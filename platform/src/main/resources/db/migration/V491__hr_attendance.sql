-- HR slice 7a: attendance.
--
-- 1. Permission HR_ATTENDANCE_MANAGE: see everyone's attendance, correct or add entries, and manage schedules. Given to roles that
--    already hold HR_MANAGE. Any employee linked to a user can clock themselves in and out and see their own record without it.
-- 2. Work schedules (ordinary hours). A tenant with none gets a standard Monday-to-Friday week the first time one is needed.
-- 3. Which schedule an employee works. An employee with none uses the tenant's default.
-- 4. Clock entries, one row per time in / time out. Entries are never deleted: a wrong one is corrected or voided, and every change
--    is recorded in a log that cannot be edited.

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'HR_ATTENDANCE_MANAGE', 'Manage attendance: see everyone, correct or add clock entries, manage work schedules')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, p.id
FROM role_permissions rp
JOIN permissions held ON held.id = rp.permission_id AND held.name = 'HR_MANAGE'
CROSS JOIN permissions p
WHERE p.name = 'HR_ATTENDANCE_MANAGE'
ON CONFLICT DO NOTHING;

CREATE TABLE hr_work_schedules (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL REFERENCES tenants(id),
    name           VARCHAR(100) NOT NULL,
    days           VARCHAR(40)  NOT NULL,           -- MON,TUE,...
    start_time     TIME         NOT NULL,
    end_time       TIME         NOT NULL,
    break_minutes  INT          NOT NULL DEFAULT 0 CHECK (break_minutes BETWEEN 0 AND 180),
    is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version        BIGINT       NOT NULL DEFAULT 0,
    CHECK (end_time > start_time)
);
CREATE UNIQUE INDEX hr_work_schedules_one_default ON hr_work_schedules (tenant_id) WHERE is_default AND active;

CREATE TABLE hr_employee_schedules (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID        NOT NULL REFERENCES tenants(id),
    employee_id  UUID        NOT NULL REFERENCES hr_employees(id),
    schedule_id  UUID        NOT NULL REFERENCES hr_work_schedules(id),
    assigned_by  UUID,
    assigned_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version      BIGINT      NOT NULL DEFAULT 0,
    UNIQUE (employee_id)
);

CREATE TABLE hr_attendance_entries (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID        NOT NULL REFERENCES tenants(id),
    employee_id  UUID        NOT NULL REFERENCES hr_employees(id),
    work_date    DATE        NOT NULL,              -- the South African date of the time in
    clock_in     TIMESTAMPTZ NOT NULL,
    clock_out    TIMESTAMPTZ,
    source       VARCHAR(10) NOT NULL CHECK (source IN ('CLOCK', 'MANUAL')),
    status       VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'VOID')),
    created_by   UUID,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version      BIGINT      NOT NULL DEFAULT 0,
    CHECK (clock_out IS NULL OR clock_out > clock_in)
);
CREATE INDEX hr_attendance_entries_employee_date ON hr_attendance_entries (employee_id, work_date);
CREATE INDEX hr_attendance_entries_tenant_date ON hr_attendance_entries (tenant_id, work_date);
-- A person cannot be clocked in twice at once.
CREATE UNIQUE INDEX hr_attendance_entries_one_open ON hr_attendance_entries (employee_id) WHERE clock_out IS NULL AND status = 'ACTIVE';

CREATE TABLE hr_attendance_log (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID        NOT NULL REFERENCES tenants(id),
    entry_id     UUID        NOT NULL REFERENCES hr_attendance_entries(id),
    action       VARCHAR(10) NOT NULL CHECK (action IN ('ADD', 'CLOCK_OUT', 'CORRECT', 'VOID')),
    old_in       TIMESTAMPTZ,
    old_out      TIMESTAMPTZ,
    new_in       TIMESTAMPTZ,
    new_out      TIMESTAMPTZ,
    reason       VARCHAR(500) NOT NULL,
    done_by      UUID,
    done_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX hr_attendance_log_entry ON hr_attendance_log (entry_id);

CREATE FUNCTION hr_attendance_log_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'The attendance log cannot be changed or deleted.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER hr_attendance_log_no_update BEFORE UPDATE OR DELETE ON hr_attendance_log
    FOR EACH ROW EXECUTE FUNCTION hr_attendance_log_immutable();
