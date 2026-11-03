-- Chunk 2b: project membership + member audit trail.
-- Rename with your next Flyway version number. Requires PostgreSQL 13+ (gen_random_uuid()).

CREATE TABLE IF NOT EXISTS project_members (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    project_id  UUID         NOT NULL REFERENCES projects(id),
    user_id     UUID         NOT NULL,
    user_name   VARCHAR(255),
    role        VARCHAR(30)  NOT NULL,
    added_by    UUID,
    added_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_project_member UNIQUE (project_id, user_id),
    CONSTRAINT project_members_role_check CHECK (role IN
        ('SPONSOR','MANAGER','PLANNER','COST_CONTROLLER','SITE_SUPERVISOR','MEMBER','VIEWER'))
);
CREATE INDEX IF NOT EXISTS idx_project_members_user ON project_members (tenant_id, user_id);

-- Append-only audit of membership changes (who was added/changed/removed, by whom).
CREATE TABLE IF NOT EXISTS project_member_events (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    project_id  UUID         NOT NULL REFERENCES projects(id),
    user_id     UUID         NOT NULL,
    action      VARCHAR(20)  NOT NULL,          -- ADDED | ROLE_CHANGED | REMOVED
    old_role    VARCHAR(30),
    new_role    VARCHAR(30),
    actor_id    UUID,
    actor_name  VARCHAR(255),
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_project_member_events_project
    ON project_member_events (tenant_id, project_id, occurred_at DESC);

-- BACKFILL so turning restriction on does not lock out the people already running projects.
-- Order matters: first role wins when one person holds several (unique per project+user).
INSERT INTO project_members (id, tenant_id, project_id, user_id, user_name, role, added_by, added_at)
SELECT gen_random_uuid(), tenant_id, id, project_manager_id, project_manager_name, 'MANAGER', created_by, now()
FROM projects WHERE project_manager_id IS NOT NULL
ON CONFLICT (project_id, user_id) DO NOTHING;

INSERT INTO project_members (id, tenant_id, project_id, user_id, user_name, role, added_by, added_at)
SELECT gen_random_uuid(), tenant_id, id, created_by, NULL, 'MANAGER', created_by, now()
FROM projects WHERE created_by IS NOT NULL
ON CONFLICT (project_id, user_id) DO NOTHING;

-- sponsor_id exists once the chunk-1 migration has run.
INSERT INTO project_members (id, tenant_id, project_id, user_id, user_name, role, added_by, added_at)
SELECT gen_random_uuid(), tenant_id, id, sponsor_id, sponsor_name, 'SPONSOR', created_by, now()
FROM projects WHERE sponsor_id IS NOT NULL
ON CONFLICT (project_id, user_id) DO NOTHING;