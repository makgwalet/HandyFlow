-- Patch 0572: a log of project detail edits (other activity is read from the existing histories), and why time was rejected.
CREATE TABLE IF NOT EXISTS project_activity (
    id          UUID PRIMARY KEY,
    tenant_id   UUID          NOT NULL,
    project_id  UUID          NOT NULL REFERENCES projects(id),
    kind        VARCHAR(30)   NOT NULL,           -- DETAILS_EDITED | CONTRACT_LINKED | CONTRACT_UNLINKED
    summary     VARCHAR(500)  NOT NULL,
    detail      TEXT,
    actor_id    UUID,
    actor_name  VARCHAR(255),
    occurred_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_project_activity_project ON project_activity (tenant_id, project_id, occurred_at DESC);

ALTER TABLE time_entries
    ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS rejected_at      TIMESTAMPTZ;
