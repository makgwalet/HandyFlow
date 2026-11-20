-- V831: Legal Practice — matter tasks and workflow templates. Confirm the next free Flyway version (V830 is the document hash column fix).
--
-- A task belongs to a matter, has one assignee, a due date and a priority, and moves OPEN -> IN_PROGRESS -> DONE (or CANCELLED with a reason).
-- A workflow template is a named list of steps; applying it to a matter creates one task per step, due start date + the step's offset in days.
-- Tasks are never deleted and the task history is append-only. Column lengths match the entities exactly (Hibernate validates the schema).

CREATE TABLE IF NOT EXISTS lp_tasks (
    id                UUID PRIMARY KEY,
    tenant_id         UUID          NOT NULL,
    matter_id         UUID          NOT NULL,
    client_id         UUID          NOT NULL,
    title             VARCHAR(200)  NOT NULL,
    description       VARCHAR(2000),
    priority          VARCHAR(10)   NOT NULL DEFAULT 'NORMAL',
    status            VARCHAR(12)   NOT NULL DEFAULT 'OPEN',
    assignee_id       UUID,
    assignee_name     VARCHAR(255),
    due_date          DATE,
    completed_at      TIMESTAMPTZ,
    completed_by      UUID,
    completed_by_name VARCHAR(255),
    cancel_reason     VARCHAR(1000),
    workflow_run_id   UUID,
    created_by        UUID          NOT NULL,
    created_by_name   VARCHAR(255),
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_lp_task_title    CHECK (char_length(btrim(title)) >= 2),
    CONSTRAINT ck_lp_task_priority CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
    CONSTRAINT ck_lp_task_status   CHECK (status IN ('OPEN','IN_PROGRESS','DONE','CANCELLED')),
    CONSTRAINT ck_lp_task_done     CHECK ((status = 'DONE') = (completed_at IS NOT NULL AND completed_by IS NOT NULL)),
    CONSTRAINT ck_lp_task_cancel   CHECK ((status = 'CANCELLED') = (cancel_reason IS NOT NULL AND char_length(btrim(cancel_reason)) >= 10)),
    CONSTRAINT ck_lp_task_assignee CHECK ((assignee_id IS NULL) = (assignee_name IS NULL))
);
CREATE INDEX IF NOT EXISTS idx_lp_task_matter   ON lp_tasks (tenant_id, matter_id, status);
CREATE INDEX IF NOT EXISTS idx_lp_task_assignee ON lp_tasks (tenant_id, assignee_id, status, due_date);
CREATE INDEX IF NOT EXISTS idx_lp_task_due      ON lp_tasks (tenant_id, due_date) WHERE status IN ('OPEN','IN_PROGRESS');

CREATE TABLE IF NOT EXISTS lp_task_events (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    task_id       UUID         NOT NULL REFERENCES lp_tasks(id),
    matter_id     UUID         NOT NULL,
    event_type    VARCHAR(20)  NOT NULL,
    actor_id      UUID         NOT NULL,
    actor_name    VARCHAR(255),
    detail        VARCHAR(1200),
    occurred_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_lp_te_type CHECK (event_type IN ('CREATED','EDITED','REASSIGNED','STARTED','STOPPED','COMPLETED','REOPENED','CANCELLED'))
);
CREATE INDEX IF NOT EXISTS idx_lp_te_task ON lp_task_events (tenant_id, task_id, occurred_at DESC);

CREATE TABLE IF NOT EXISTS lp_workflow_templates (
    id              UUID PRIMARY KEY,
    tenant_id       UUID          NOT NULL,
    name            VARCHAR(120)  NOT NULL,
    description     VARCHAR(1000),
    matter_type     VARCHAR(50),
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by      UUID          NOT NULL,
    created_by_name VARCHAR(255),
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_lp_wt_name CHECK (char_length(btrim(name)) >= 2)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_lp_wt_name ON lp_workflow_templates (tenant_id, lower(name)) WHERE active;

CREATE TABLE IF NOT EXISTS lp_workflow_steps (
    id             UUID PRIMARY KEY,
    tenant_id      UUID          NOT NULL,
    template_id    UUID          NOT NULL REFERENCES lp_workflow_templates(id),
    step_no        INTEGER       NOT NULL,
    title          VARCHAR(200)  NOT NULL,
    description    VARCHAR(2000),
    priority       VARCHAR(10)   NOT NULL DEFAULT 'NORMAL',
    offset_days    INTEGER       NOT NULL DEFAULT 0,
    assign_to      VARCHAR(10)   NOT NULL DEFAULT 'STARTER',
    CONSTRAINT uq_lp_ws_no UNIQUE (template_id, step_no),
    CONSTRAINT ck_lp_ws_no       CHECK (step_no BETWEEN 1 AND 30),
    CONSTRAINT ck_lp_ws_title    CHECK (char_length(btrim(title)) >= 2),
    CONSTRAINT ck_lp_ws_priority CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
    CONSTRAINT ck_lp_ws_offset   CHECK (offset_days BETWEEN 0 AND 730),
    CONSTRAINT ck_lp_ws_assign   CHECK (assign_to IN ('STARTER','NONE'))
);

CREATE TABLE IF NOT EXISTS lp_workflow_runs (
    id               UUID PRIMARY KEY,
    tenant_id        UUID          NOT NULL,
    template_id      UUID          NOT NULL REFERENCES lp_workflow_templates(id),
    template_name    VARCHAR(120)  NOT NULL,
    matter_id        UUID          NOT NULL,
    start_date       DATE          NOT NULL,
    tasks_created    INTEGER       NOT NULL,
    applied_by       UUID          NOT NULL,
    applied_by_name  VARCHAR(255),
    applied_at       TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_lp_wr_once UNIQUE (tenant_id, matter_id, template_id),
    CONSTRAINT ck_lp_wr_count CHECK (tasks_created >= 1)
);

CREATE OR REPLACE FUNCTION lp_task_event_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only: % is not allowed', TG_TABLE_NAME, TG_OP USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;
-- Tasks and runs are never deleted; the matter, client and creator of a task never change; a finished task keeps its completion.
CREATE OR REPLACE FUNCTION lp_task_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN RAISE EXCEPTION '% rows are never deleted', TG_TABLE_NAME USING ERRCODE = 'integrity_constraint_violation'; END IF;
    IF TG_TABLE_NAME = 'lp_tasks' THEN
        IF NEW.id <> OLD.id OR NEW.tenant_id <> OLD.tenant_id OR NEW.matter_id <> OLD.matter_id OR NEW.client_id <> OLD.client_id OR NEW.created_by <> OLD.created_by THEN
            RAISE EXCEPTION 'a task cannot be moved to another matter or client' USING ERRCODE = 'integrity_constraint_violation';
        END IF;
        IF OLD.status = 'CANCELLED' THEN
            RAISE EXCEPTION 'a cancelled task cannot be changed' USING ERRCODE = 'integrity_constraint_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_lp_task_guard ON lp_tasks;
CREATE TRIGGER trg_lp_task_guard BEFORE UPDATE OR DELETE ON lp_tasks FOR EACH ROW EXECUTE FUNCTION lp_task_guard();
DROP TRIGGER IF EXISTS trg_lp_wr_no_change ON lp_workflow_runs;
CREATE TRIGGER trg_lp_wr_no_change BEFORE UPDATE OR DELETE ON lp_workflow_runs FOR EACH ROW EXECUTE FUNCTION lp_task_event_append_only();

DROP TRIGGER IF EXISTS trg_lp_te_append_only ON lp_task_events;
CREATE TRIGGER trg_lp_te_append_only BEFORE UPDATE OR DELETE ON lp_task_events FOR EACH ROW EXECUTE FUNCTION lp_task_event_append_only();

-- Permissions -------------------------------------------------------------------------------------------------------
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'LEGALPRACTICE_TASK_WRITE',      'Create and work matter tasks: assign, start, complete, reopen, cancel'),
    (gen_random_uuid(), 'LEGALPRACTICE_WORKFLOW_MANAGE', 'Create and change workflow templates, and apply them to matters')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions SET module_key = 'legalpractice', resource = CASE name WHEN 'LEGALPRACTICE_TASK_WRITE' THEN 'task' ELSE 'workflow' END,
       verb = CASE name WHEN 'LEGALPRACTICE_TASK_WRITE' THEN 'WRITE' ELSE 'MANAGE' END,
       sensitivity = 'NORMAL', requires_second_person = FALSE, grantable_by = 'ADMIN'
 WHERE name IN ('LEGALPRACTICE_TASK_WRITE', 'LEGALPRACTICE_WORKFLOW_MANAGE');

-- Whoever could manage matters (MANAGE or ADMIN) gets both. Narrow in Roles.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions src ON src.id = rp.permission_id AND src.name IN ('LEGALPRACTICE_MANAGE', 'LEGALPRACTICE_ADMIN')
  JOIN permissions np ON np.name IN ('LEGALPRACTICE_TASK_WRITE', 'LEGALPRACTICE_WORKFLOW_MANAGE')
 WHERE NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id);
