-- V611: Warehousing task engine. Picking and put-away become tasks that a person claims, does and completes.
--
-- A PICK task is one stop on a pick walk: take this quantity of this item (and lot) from this location for this order. They are
-- created when picking starts, from the order's allocations, and carry the location's walking order so a pick list reads in the
-- order the floor is walked. A PUTAWAY task moves received stock from a dock or staging location to a storage location.
-- A short pick (less found than allocated) is kept as SHORT with a reason, and the order cannot be packed or shipped until it is dealt with.

CREATE TABLE whse_tasks (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    task_type         VARCHAR(10) NOT NULL CHECK (task_type IN ('PICK','PUTAWAY')),
    status            VARCHAR(12) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','IN_PROGRESS','DONE','SHORT','CANCELLED')),
    priority          INTEGER NOT NULL DEFAULT 5 CHECK (priority BETWEEN 1 AND 9),
    client_id         UUID NOT NULL REFERENCES whse_clients (id),
    item_id           UUID NOT NULL REFERENCES whse_items (id),
    lot_id            UUID REFERENCES whse_lots (id),
    from_location_id  UUID NOT NULL REFERENCES whse_locations (id),
    to_location_id    UUID REFERENCES whse_locations (id),
    qty               NUMERIC(12,3) NOT NULL CHECK (qty > 0),
    qty_done          NUMERIC(12,3) NOT NULL DEFAULT 0 CHECK (qty_done >= 0),
    pick_sequence     INTEGER,
    reference_type    VARCHAR(30),
    reference_id      UUID,
    reference_number  VARCHAR(100),
    order_line_id     UUID REFERENCES whse_outbound_order_lines (id),
    assigned_to       UUID,
    assigned_at       TIMESTAMPTZ,
    completed_by      UUID,
    completed_at      TIMESTAMPTZ,
    note              VARCHAR(300),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_whse_task_qty_done   CHECK (qty_done <= qty),
    CONSTRAINT chk_whse_task_done       CHECK (status <> 'DONE' OR qty_done > 0),
    CONSTRAINT chk_whse_task_short      CHECK (status <> 'SHORT' OR (task_type = 'PICK' AND qty_done < qty AND note IS NOT NULL AND length(trim(note)) >= 5)),
    CONSTRAINT chk_whse_task_cancelled  CHECK (status <> 'CANCELLED' OR (note IS NOT NULL AND length(trim(note)) >= 5)),
    CONSTRAINT chk_whse_task_pick_dest  CHECK (task_type <> 'PICK' OR to_location_id IS NULL),
    CONSTRAINT chk_whse_task_claimed    CHECK (status <> 'IN_PROGRESS' OR assigned_to IS NOT NULL),
    CONSTRAINT chk_whse_task_done_by    CHECK (status NOT IN ('DONE','SHORT') OR (completed_by IS NOT NULL AND completed_at IS NOT NULL))
);

CREATE INDEX idx_whse_tasks_worklist ON whse_tasks (tenant_id, task_type, status, priority, pick_sequence);
CREATE INDEX idx_whse_tasks_reference ON whse_tasks (tenant_id, reference_type, reference_id);
CREATE INDEX idx_whse_tasks_assignee ON whse_tasks (tenant_id, assigned_to) WHERE status = 'IN_PROGRESS';

-- The same pick cannot be created twice while it is live.
CREATE UNIQUE INDEX uq_whse_tasks_live_pick ON whse_tasks
    (tenant_id, reference_id, order_line_id, from_location_id, COALESCE(lot_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE task_type = 'PICK' AND status <> 'CANCELLED';
