-- Chunk 3: scheduling — per-task baselines + dependency integrity.
-- Rename with your next Flyway version number.

ALTER TABLE project_tasks
    ADD COLUMN IF NOT EXISTS baseline_start DATE,
    ADD COLUMN IF NOT EXISTS baseline_end   DATE;

-- Best-effort baseline for projects already past the planning stage: the task's CURRENT
-- planned dates. This is NOT the true original plan (that was never captured) — it is
-- simply the earliest point from which slippage can be measured. New projects capture a
-- real baseline at approval / first activation.
UPDATE project_tasks t
SET baseline_start = t.planned_start,
    baseline_end   = t.planned_end
FROM projects p
WHERE p.id = t.project_id
  AND p.status IN ('APPROVED','ACTIVE','ON_HOLD','COMPLETED','CLOSED')
  AND t.baseline_start IS NULL
  AND t.planned_start IS NOT NULL
  AND t.planned_end   IS NOT NULL;

-- Dependency integrity. No API created dependency rows before this chunk, so these should
-- apply cleanly; if the unique index fails, de-duplicate task_dependencies first.
CREATE UNIQUE INDEX IF NOT EXISTS uq_task_dependency_pair
    ON task_dependencies (predecessor_id, successor_id);
CREATE INDEX IF NOT EXISTS idx_task_dependencies_successor
    ON task_dependencies (successor_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_task_dependency_no_self') THEN
        ALTER TABLE task_dependencies
            ADD CONSTRAINT ck_task_dependency_no_self CHECK (predecessor_id <> successor_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_task_dependency_type') THEN
        ALTER TABLE task_dependencies
            ADD CONSTRAINT ck_task_dependency_type CHECK (dependency_type IN ('FS','SS','FF','SF'));
    END IF;
END $$;