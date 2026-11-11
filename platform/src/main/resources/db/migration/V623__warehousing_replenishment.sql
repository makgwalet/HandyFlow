-- V623: Replenishment. A pick face (the location orders are picked from) gets a minimum and a maximum for an item. When what is available
-- there falls below the minimum, REPLEN tasks move stock from the reserve locations to fill it back up to the maximum.
--
-- These are per-location levels, not the item's reorder levels in the item master (min_qty / max_qty there are for buying more stock).

CREATE TABLE whse_replen_rules (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    client_id   UUID NOT NULL REFERENCES whse_clients (id),
    item_id     UUID NOT NULL REFERENCES whse_items (id),
    location_id UUID NOT NULL REFERENCES whse_locations (id),
    min_qty     NUMERIC(12,3) NOT NULL CHECK (min_qty >= 0),
    max_qty     NUMERIC(12,3) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_whse_replen_max CHECK (max_qty > min_qty),
    CONSTRAINT uq_whse_replen_rule UNIQUE (tenant_id, client_id, item_id, location_id)
);

ALTER TABLE whse_tasks DROP CONSTRAINT IF EXISTS whse_tasks_task_type_check;
ALTER TABLE whse_tasks ADD CONSTRAINT chk_whse_task_type CHECK (task_type IN ('PICK','PUTAWAY','REPLEN'));
ALTER TABLE whse_tasks ADD CONSTRAINT chk_whse_task_replen_dest CHECK (task_type <> 'REPLEN' OR to_location_id IS NOT NULL);

-- The same replenishment cannot be created twice while it is live.
CREATE UNIQUE INDEX uq_whse_tasks_live_replen ON whse_tasks
    (tenant_id, item_id, from_location_id, to_location_id, COALESCE(lot_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE task_type = 'REPLEN' AND status IN ('OPEN','IN_PROGRESS');
