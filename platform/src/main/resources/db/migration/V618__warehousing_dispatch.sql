-- V618: dispatch stages. A manifest puts PACKED orders on one vehicle: OPEN -> LOADING -> DISPATCHED (dispatching ships every
-- order on it). After shipping, each order gets a proof-of-delivery record. "Delivered" is a flag on the order rather than a new
-- status, so billing, returns and the portal that read SHIPPED are unaffected.
CREATE TABLE whse_dispatch_manifests (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    manifest_number       VARCHAR(30) NOT NULL,
    carrier               VARCHAR(120),
    vehicle_registration  VARCHAR(30),
    driver_name           VARCHAR(120),
    notes                 VARCHAR(300),
    status                VARCHAR(12) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','LOADING','DISPATCHED','CANCELLED')),
    created_by            UUID NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    loading_started_at    TIMESTAMPTZ,
    loading_started_by    UUID,
    dispatched_at         TIMESTAMPTZ,
    dispatched_by         UUID,
    cancelled_at          TIMESTAMPTZ,
    cancelled_by          UUID,
    cancel_reason         VARCHAR(300),
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_whse_manifest_number UNIQUE (tenant_id, manifest_number),
    CONSTRAINT chk_whse_manifest_loading CHECK ((status IN ('LOADING','DISPATCHED')) = (loading_started_at IS NOT NULL) OR status = 'CANCELLED'),
    CONSTRAINT chk_whse_manifest_dispatched CHECK ((status = 'DISPATCHED') = (dispatched_at IS NOT NULL)),
    CONSTRAINT chk_whse_manifest_cancelled CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL AND cancel_reason IS NOT NULL))
);
CREATE INDEX idx_whse_manifest_open ON whse_dispatch_manifests (tenant_id, created_at DESC);

ALTER TABLE whse_outbound_orders ADD COLUMN IF NOT EXISTS manifest_id UUID REFERENCES whse_dispatch_manifests (id);
ALTER TABLE whse_outbound_orders ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMPTZ;
ALTER TABLE whse_outbound_orders ADD COLUMN IF NOT EXISTS delivered_by UUID;
ALTER TABLE whse_outbound_orders ADD COLUMN IF NOT EXISTS pod_received_by VARCHAR(120);
ALTER TABLE whse_outbound_orders ADD COLUMN IF NOT EXISTS pod_note VARCHAR(300);
ALTER TABLE whse_outbound_orders ADD CONSTRAINT chk_whse_order_delivery CHECK (
    delivered_at IS NULL OR (status = 'SHIPPED' AND pod_received_by IS NOT NULL AND delivered_by IS NOT NULL));
CREATE INDEX idx_whse_outbound_manifest ON whse_outbound_orders (manifest_id) WHERE manifest_id IS NOT NULL;
