-- Client-facing note when a portal request is declined (the internal rejection_reason stays staff-only).
ALTER TABLE fm_work_orders ADD COLUMN IF NOT EXISTS client_message VARCHAR(1000);
