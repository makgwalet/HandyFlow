-- V614: Resolving a short pick without cancelling the order.
--
-- A pick task that found less than was allocated is SHORT and blocks packing and shipping. It can now be closed (SHORT_CLOSED) in one of
-- three ways, recorded in `resolution`:
--   SHIP_SHORT        the line ships what was found; the rest of its allocation is released and recorded on the line as qty_short
--   SOURCE_ELSEWHERE  (lot-tracked line) what was found ships; the shortfall is allocated again from other positions, first expiry first
--   MOVE_LINE         (line that is not lot tracked) the whole line is allocated again at another location, with a new pick task

ALTER TABLE whse_outbound_order_lines ADD COLUMN qty_short NUMERIC(12,3) NOT NULL DEFAULT 0;
ALTER TABLE whse_outbound_order_lines ADD CONSTRAINT chk_whse_line_short CHECK (qty_short >= 0 AND qty_short <= qty_ordered);

ALTER TABLE whse_tasks DROP CONSTRAINT whse_tasks_status_check;
ALTER TABLE whse_tasks ADD CONSTRAINT chk_whse_task_status CHECK (status IN ('OPEN','IN_PROGRESS','DONE','SHORT','SHORT_CLOSED','CANCELLED'));
ALTER TABLE whse_tasks ADD COLUMN resolution VARCHAR(20);
ALTER TABLE whse_tasks ADD COLUMN resolution_note VARCHAR(300);
ALTER TABLE whse_tasks ADD COLUMN resolved_by UUID;
ALTER TABLE whse_tasks ADD COLUMN resolved_at TIMESTAMPTZ;
ALTER TABLE whse_tasks ADD CONSTRAINT chk_whse_task_short_closed CHECK (
    (status = 'SHORT_CLOSED') = (resolution IS NOT NULL)
    AND (status <> 'SHORT_CLOSED' OR (
        task_type = 'PICK' AND qty_done < qty
        AND resolution IN ('SHIP_SHORT','SOURCE_ELSEWHERE','MOVE_LINE')
        AND resolution_note IS NOT NULL AND length(trim(resolution_note)) >= 5
        AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL)));
