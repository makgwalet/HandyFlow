-- Chunk 7: RFI management — assignment, full response history, impact flags, overdue escalation.
-- Rename with your next Flyway version number. Requires PostgreSQL 13+.

ALTER TABLE project_rfis
    ADD COLUMN IF NOT EXISTS assigned_to_id      UUID,
    ADD COLUMN IF NOT EXISTS assigned_to_name    VARCHAR(255),
    ADD COLUMN IF NOT EXISTS assigned_at         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS clarification_count INT         NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS escalation_level    INT         NOT NULL DEFAULT 0,   -- 0 none, 1 overdue, 2 a week, 3 two weeks
    ADD COLUMN IF NOT EXISTS impact_task_id      UUID,
    ADD COLUMN IF NOT EXISTS impacts_schedule    BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS impacts_cost        BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS impact_notes        TEXT,
    ADD COLUMN IF NOT EXISTS no_change_note      TEXT;

-- Append-only history. Unlike the single `response` column (overwritten by a later answer),
-- every response, clarification request, reassignment and closure stays readable here.
CREATE TABLE IF NOT EXISTS project_rfi_events (
    id           UUID PRIMARY KEY,
    tenant_id    UUID         NOT NULL,
    project_id   UUID         NOT NULL REFERENCES projects(id),
    rfi_id       UUID         NOT NULL REFERENCES project_rfis(id),
    event_type   VARCHAR(30)  NOT NULL,
    comment      TEXT,
    actor_id     UUID,
    actor_name   VARCHAR(255),
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_rfi_events_rfi ON project_rfi_events (rfi_id, occurred_at);

-- Existing answers become the first entries of that history, so nothing already recorded is lost.
INSERT INTO project_rfi_events (id, tenant_id, project_id, rfi_id, event_type, comment, actor_id, actor_name, occurred_at)
SELECT gen_random_uuid(), r.tenant_id, r.project_id, r.id, 'RESPONDED', r.response,
       r.responded_by_id, r.responded_by,
       COALESCE(r.responded_date::timestamptz, r.updated_at::timestamptz, now())
FROM project_rfis r
WHERE r.response IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM project_rfi_events e WHERE e.rfi_id = r.id AND e.event_type = 'RESPONDED');

-- Speeds up the nightly overdue sweep.
CREATE INDEX IF NOT EXISTS idx_project_rfis_overdue
    ON project_rfis (due_date) WHERE status = 'SUBMITTED';