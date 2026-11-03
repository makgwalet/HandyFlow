-- Chunk 1: Projects governance & lifecycle.
-- Rename with your next Flyway version number (e.g. V131__projects_governance.sql).
-- NOTE: timestamp columns use TIMESTAMPTZ; if your existing projects.created_at is
-- TIMESTAMP (without tz), switch these to match so Hibernate validation stays happy.

ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS sponsor_id        UUID,
    ADD COLUMN IF NOT EXISTS sponsor_name      VARCHAR(255),
    ADD COLUMN IF NOT EXISTS objectives        TEXT,
    ADD COLUMN IF NOT EXISTS scope_statement   TEXT,
    ADD COLUMN IF NOT EXISTS business_case     TEXT,
    ADD COLUMN IF NOT EXISTS proposed_at       TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS approved_at       TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS approved_by       UUID,
    ADD COLUMN IF NOT EXISTS approved_by_name  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS closed_at         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS closed_by         UUID,
    ADD COLUMN IF NOT EXISTS closure_notes     TEXT,
    ADD COLUMN IF NOT EXISTS lessons_learned   TEXT,
    ADD COLUMN IF NOT EXISTS version           BIGINT NOT NULL DEFAULT 0;

-- Replace the status CHECK constraint (new states PROPOSED, APPROVED, CLOSED).
-- Review first: this drops every CHECK constraint on projects whose definition mentions "status".
DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'projects'::regclass
               AND contype = 'c'
               AND pg_get_constraintdef(oid) ILIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE projects DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;

ALTER TABLE projects
    ADD CONSTRAINT projects_status_check
    CHECK (status IN ('PLANNING','PROPOSED','APPROVED','ACTIVE','ON_HOLD','COMPLETED','CLOSED','CANCELLED'));

-- Append-only audit trail of every status change.
CREATE TABLE IF NOT EXISTS project_status_history (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL,
    project_id       UUID         NOT NULL REFERENCES projects(id),
    from_status      VARCHAR(20),
    to_status        VARCHAR(20)  NOT NULL,
    action           VARCHAR(30)  NOT NULL,
    reason           TEXT,
    changed_by       UUID,
    changed_by_name  VARCHAR(255),
    changed_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_project_status_history_project
    ON project_status_history (tenant_id, project_id, changed_at DESC);