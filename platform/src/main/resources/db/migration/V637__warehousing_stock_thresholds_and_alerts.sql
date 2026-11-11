-- Warehousing: stock thresholds per warehouse, and alerts with a life of their own.
-- A warehouse-specific minimum/maximum for an item overrides the item's own defaults (whse_items.min_qty / max_qty), value by value; with neither there is no
-- threshold alert. Alerts are kept so they can be acknowledged and resolved: at most one live alert per item and warehouse, and one the user resolved by hand is
-- not raised again until the condition has cleared and come back.

CREATE TABLE IF NOT EXISTS whse_stock_thresholds (
    id           UUID PRIMARY KEY,
    tenant_id    UUID          NOT NULL,
    warehouse_id UUID          NOT NULL,
    item_id      UUID          NOT NULL REFERENCES whse_items (id),
    min_qty      NUMERIC(12,3) CHECK (min_qty IS NULL OR min_qty >= 0),
    max_qty      NUMERIC(12,3) CHECK (max_qty IS NULL OR max_qty > 0),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by   UUID,
    CONSTRAINT uq_whse_threshold UNIQUE (tenant_id, warehouse_id, item_id),
    CONSTRAINT fk_whse_threshold_warehouse FOREIGN KEY (tenant_id, warehouse_id) REFERENCES whse_warehouses (tenant_id, id),
    CONSTRAINT chk_whse_threshold_any CHECK (min_qty IS NOT NULL OR max_qty IS NOT NULL),
    CONSTRAINT chk_whse_threshold_order CHECK (min_qty IS NULL OR max_qty IS NULL OR max_qty > min_qty)
);

CREATE TABLE IF NOT EXISTS whse_stock_alerts (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID          NOT NULL,
    warehouse_id       UUID          NOT NULL,
    item_id            UUID          NOT NULL REFERENCES whse_items (id),
    client_id          UUID          NOT NULL,
    kind               VARCHAR(15)   NOT NULL CHECK (kind IN ('OUT_OF_STOCK', 'BELOW_MIN', 'ABOVE_MAX')),
    status             VARCHAR(12)   NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
    first_seen         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    last_seen          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    available          NUMERIC(14,3) NOT NULL,
    min_qty            NUMERIC(12,3),
    max_qty            NUMERIC(12,3),
    recommended_order  NUMERIC(14,3),
    acknowledged_by    UUID,
    acknowledged_at    TIMESTAMPTZ,
    resolved_by        UUID,
    resolved_at        TIMESTAMPTZ,
    resolution         VARCHAR(10)   CHECK (resolution IN ('CLEARED', 'CHANGED', 'MANUAL')),
    resolution_note    VARCHAR(300),
    suppressed         BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_whse_alert_warehouse FOREIGN KEY (tenant_id, warehouse_id) REFERENCES whse_warehouses (tenant_id, id),
    CONSTRAINT chk_whse_alert_resolved CHECK ((status = 'RESOLVED') = (resolved_at IS NOT NULL AND resolution IS NOT NULL)),
    CONSTRAINT chk_whse_alert_manual CHECK (resolution IS DISTINCT FROM 'MANUAL' OR (resolved_by IS NOT NULL AND length(btrim(resolution_note)) >= 5)),
    CONSTRAINT chk_whse_alert_suppressed CHECK (NOT suppressed OR resolution = 'MANUAL')
);
-- One live alert per item and warehouse.
CREATE UNIQUE INDEX IF NOT EXISTS uq_whse_alert_live ON whse_stock_alerts (tenant_id, warehouse_id, item_id) WHERE status <> 'RESOLVED';
CREATE INDEX IF NOT EXISTS idx_whse_alert_list ON whse_stock_alerts (tenant_id, status, warehouse_id);
