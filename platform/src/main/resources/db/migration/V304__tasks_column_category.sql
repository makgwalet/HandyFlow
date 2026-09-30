-- V304__tasks_column_category.sql
--
-- Gives every task column an explicit CATEGORY, so a task's status comes from the column it
-- sits in instead of being guessed from the column's NAME ("PROGRESS", "REVIEW", ...).
--
-- WHY: TasksService.deriveStatus() guessed a status from the column name, so a column called
-- "Blocked" or "Backlog" silently became TODO, and the guess was then written with a raw JDBC
-- UPDATE that Hibernate's own flush of the loaded Task could overwrite (Task has no
-- @DynamicUpdate, so its UPDATE writes every column). The result: status only ever changed
-- for moves INTO a done column, the dashboard's In Progress / In Review counts were stale,
-- and a task moved back OUT of Done kept status DONE (so it could never be flagged overdue).
-- getSummary() and the board UI already worked around this ("the status field may lag").
--
-- Category values match tasks.status, plus BLOCKED.

-- 1. The category column (existing rows are backfilled below; new columns default to TODO).
ALTER TABLE task_columns ADD COLUMN IF NOT EXISTS category VARCHAR(20) NOT NULL DEFAULT 'TODO';

ALTER TABLE task_columns DROP CONSTRAINT IF EXISTS chk_task_columns_category;
ALTER TABLE task_columns ADD CONSTRAINT chk_task_columns_category
    CHECK (category IN ('TODO','IN_PROGRESS','IN_REVIEW','BLOCKED','DONE'));

-- Backfill mirrors the old name-guessing (so nothing changes behind anyone's back), plus
-- BLOCKED. Admins can correct any column's category in the board settings afterwards.
UPDATE task_columns SET category = CASE
        WHEN is_done_column                                              THEN 'DONE'
        WHEN upper(name) LIKE '%BLOCK%'                                  THEN 'BLOCKED'
        WHEN upper(name) LIKE '%REVIEW%'   OR upper(name) LIKE '%TESTING%' THEN 'IN_REVIEW'
        WHEN upper(name) LIKE '%PROGRESS%' OR upper(name) LIKE '%DOING%'   THEN 'IN_PROGRESS'
        ELSE 'TODO'
    END;

-- 2. tasks.status may now be BLOCKED. (The original CHECK was unnamed: tasks_status_check.)
ALTER TABLE tasks DROP CONSTRAINT IF EXISTS tasks_status_check;
ALTER TABLE tasks DROP CONSTRAINT IF EXISTS chk_tasks_status;
ALTER TABLE tasks ADD CONSTRAINT chk_tasks_status
    CHECK (status IN ('TODO','IN_PROGRESS','IN_REVIEW','BLOCKED','DONE','CANCELLED'));

-- 3. Heal tasks whose status drifted from their column. CANCELLED is deliberate, so it is
-- left alone. completed_at follows the column: set when it is a done column, cleared otherwise.
UPDATE tasks t
   SET status       = c.category,
       completed_at = CASE WHEN c.category = 'DONE' THEN COALESCE(t.completed_at, NOW()) ELSE NULL END
  FROM task_columns c
 WHERE c.id = t.column_id
   AND t.status <> 'CANCELLED'
   AND t.status <> c.category;

COMMENT ON COLUMN task_columns.category IS
    'Workflow stage of tasks in this column: TODO, IN_PROGRESS, IN_REVIEW, BLOCKED or DONE. Drives tasks.status.';
