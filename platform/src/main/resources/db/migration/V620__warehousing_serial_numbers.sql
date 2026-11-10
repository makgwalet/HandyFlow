-- Serial numbers: a register of every serial with its history. An item opts in; from then on each unit received, picked, shipped and
-- returned carries its serial. A serial is unique per client and item.
ALTER TABLE whse_items ADD COLUMN IF NOT EXISTS serial_tracked BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE whse_serials (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    client_id         UUID NOT NULL REFERENCES whse_clients (id),
    item_id           UUID NOT NULL REFERENCES whse_items (id),
    serial_number     VARCHAR(80) NOT NULL CHECK (serial_number ~ '^[A-Za-z0-9][A-Za-z0-9._/-]{0,79}$'),
    status            VARCHAR(10) NOT NULL DEFAULT 'IN_STOCK' CHECK (status IN ('IN_STOCK','PICKED','SHIPPED')),
    receipt_event_id  UUID,
    outbound_order_id UUID REFERENCES whse_outbound_orders (id),
    outbound_line_id  UUID REFERENCES whse_outbound_order_lines (id),
    shipped_at        TIMESTAMPTZ,
    return_count      INT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        UUID,
    CONSTRAINT chk_whse_serial_pick CHECK ((status IN ('PICKED','SHIPPED')) = (outbound_line_id IS NOT NULL)),
    CONSTRAINT chk_whse_serial_shipped CHECK ((status = 'SHIPPED') = (shipped_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_whse_serial ON whse_serials (tenant_id, client_id, item_id, upper(serial_number));
CREATE INDEX idx_whse_serials_line ON whse_serials (outbound_line_id) WHERE outbound_line_id IS NOT NULL;
CREATE INDEX idx_whse_serials_lookup ON whse_serials (tenant_id, upper(serial_number));

CREATE TABLE whse_serial_events (
    id         UUID PRIMARY KEY,
    tenant_id  UUID NOT NULL,
    serial_id  UUID NOT NULL REFERENCES whse_serials (id),
    event      VARCHAR(12) NOT NULL CHECK (event IN ('RECEIVED','PICKED','UNPICKED','SHIPPED','RETURNED')),
    reference  VARCHAR(100),
    actor_id   UUID,
    at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_whse_serial_events ON whse_serial_events (serial_id, at);
