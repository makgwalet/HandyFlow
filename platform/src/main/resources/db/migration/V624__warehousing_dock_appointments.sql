-- V624: Dock appointments. A carrier is booked into a dock door (a DOCK location) for a time slot, to deliver an inbound shipment or to collect
-- an outbound dispatch. A door cannot hold two live appointments that overlap (checked in the service under a lock on the door).
--
-- SCHEDULED -> ARRIVED (vehicle at the gate) -> AT_DOCK (on the door) -> COMPLETED; or CANCELLED / NO_SHOW from SCHEDULED.

CREATE TABLE whse_dock_appointments (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    direction             VARCHAR(8) NOT NULL CHECK (direction IN ('INBOUND','OUTBOUND')),
    dock_location_id      UUID NOT NULL REFERENCES whse_locations (id),
    starts_at             TIMESTAMPTZ NOT NULL,
    ends_at               TIMESTAMPTZ NOT NULL,
    carrier               VARCHAR(120),
    vehicle_registration  VARCHAR(30),
    driver_name           VARCHAR(120),
    inbound_shipment_id   UUID REFERENCES whse_inbound_shipments (id),
    manifest_id           UUID REFERENCES whse_dispatch_manifests (id),
    notes                 VARCHAR(300),
    status                VARCHAR(10) NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED','ARRIVED','AT_DOCK','COMPLETED','CANCELLED','NO_SHOW')),
    arrived_at            TIMESTAMPTZ,
    at_dock_at            TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,
    cancel_reason         VARCHAR(300),
    created_by            UUID NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_whse_dock_slot CHECK (ends_at > starts_at AND ends_at - starts_at <= interval '12 hours'),
    CONSTRAINT chk_whse_dock_link CHECK ((direction = 'INBOUND' AND manifest_id IS NULL) OR (direction = 'OUTBOUND' AND inbound_shipment_id IS NULL)),
    CONSTRAINT chk_whse_dock_cancel CHECK (status NOT IN ('CANCELLED','NO_SHOW') OR (cancel_reason IS NOT NULL AND length(trim(cancel_reason)) >= 5)),
    CONSTRAINT chk_whse_dock_arrived CHECK (status IN ('SCHEDULED','CANCELLED','NO_SHOW') OR arrived_at IS NOT NULL)
);
CREATE INDEX idx_whse_dock_door_time ON whse_dock_appointments (tenant_id, dock_location_id, starts_at);
CREATE INDEX idx_whse_dock_time ON whse_dock_appointments (tenant_id, starts_at);
-- One live appointment per shipment or manifest.
CREATE UNIQUE INDEX uq_whse_dock_live_shipment ON whse_dock_appointments (inbound_shipment_id) WHERE inbound_shipment_id IS NOT NULL AND status IN ('SCHEDULED','ARRIVED','AT_DOCK');
CREATE UNIQUE INDEX uq_whse_dock_live_manifest ON whse_dock_appointments (manifest_id) WHERE manifest_id IS NOT NULL AND status IN ('SCHEDULED','ARRIVED','AT_DOCK');
