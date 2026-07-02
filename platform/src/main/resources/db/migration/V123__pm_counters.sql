-- V89 — Atomic counter table for project-management sequence numbers
--
-- WHY THIS TABLE?
-- ───────────────
-- The previous approach was:
--   int seq = SELECT MAX(CAST(SUBSTRING(project_number, 4) AS int)) FROM projects + 1
--
-- Under concurrent load, two threads can both read MAX = 5 and both try to
-- INSERT project_number = 'PRJ0006'.  The UNIQUE constraint catches it, but
-- the application gets a 500 error with no retry.  High-volume tenants hit
-- this constantly.
--
-- The fix: PostgreSQL's atomic  INSERT … ON CONFLICT DO UPDATE … RETURNING
-- increments the counter and returns the new value in a single statement that
-- is guaranteed to be serialised per (tenant_id, counter_type) row — no race,
-- no retry logic needed in application code.

CREATE TABLE IF NOT EXISTS pm_counters (
    tenant_id     UUID         NOT NULL,
    counter_type  VARCHAR(50)  NOT NULL,
    current_value BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, counter_type)
);

COMMENT ON TABLE pm_counters IS
    'Monotonically-increasing counters for PM entity numbering (projects, tasks, change orders, etc.)';
COMMENT ON COLUMN pm_counters.counter_type IS
    'Discriminator: PROJECT | TASK_<project_id> | CO_<project_id> | SNAG_<project_id> | PHASE_<project_id> | BUDGET_<project_id>';
