-- V625: Slotting. A MOVE task relocates stock from one storage location to a fixed destination, to put fast-moving items nearer the front of the
-- pick walk. It is done through the task list like a replenishment (a transfer to its destination).

ALTER TABLE whse_tasks DROP CONSTRAINT IF EXISTS chk_whse_task_type;
ALTER TABLE whse_tasks ADD CONSTRAINT chk_whse_task_type CHECK (task_type IN ('PICK','PUTAWAY','REPLEN','MOVE'));
ALTER TABLE whse_tasks DROP CONSTRAINT IF EXISTS chk_whse_task_replen_dest;
ALTER TABLE whse_tasks ADD CONSTRAINT chk_whse_task_fixed_dest CHECK (task_type NOT IN ('REPLEN','MOVE') OR to_location_id IS NOT NULL);

-- One live move per source and destination, and one live move into a destination.
CREATE UNIQUE INDEX uq_whse_tasks_live_move_dest ON whse_tasks (tenant_id, to_location_id) WHERE task_type = 'MOVE' AND status IN ('OPEN','IN_PROGRESS');
CREATE UNIQUE INDEX uq_whse_tasks_live_move_src ON whse_tasks
    (tenant_id, item_id, from_location_id, COALESCE(lot_id, '00000000-0000-0000-0000-000000000000'::uuid)) WHERE task_type = 'MOVE' AND status IN ('OPEN','IN_PROGRESS');
