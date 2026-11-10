-- V606: Warehousing receiving with inspection and discrepancies.
--
-- A receipt now records what the dock found: good stock, damaged stock and stock held for inspection. Each part lands in the
-- matching stock status (AVAILABLE, DAMAGED, QUARANTINE), so held and damaged stock can never be picked. Held stock is cleared
-- by an inspection (pass -> AVAILABLE, fail -> DAMAGED), and the inspector must be a different person from the receiver
-- (permission WAREHOUSING_QC_RELEASE). A shipment that is short, over, damaged or carries an item that was not on the advice
-- is closed with a signed-off note. Receipt and inspection rows are append-only.

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'WAREHOUSING_QC_RELEASE', 'Pass or fail stock held for inspection that someone else received')
ON CONFLICT (name) DO NOTHING;

UPDATE permissions
   SET module_key = 'warehousing', resource = 'receiving_inspection', verb = 'APPROVE', sensitivity = 'SENSITIVE',
       requires_second_person = true, grantable_by = 'MODULE_ADMIN'
 WHERE name = 'WAREHOUSING_QC_RELEASE';

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions ap ON ap.id = rp.permission_id AND ap.name = 'WAREHOUSING_ADMIN'
  CROSS JOIN permissions np
 WHERE np.name = 'WAREHOUSING_QC_RELEASE'
ON CONFLICT DO NOTHING;

-- An item that arrived but was not on the advice is a line with nothing expected.
ALTER TABLE whse_inbound_shipment_lines ADD COLUMN IF NOT EXISTS unexpected BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE whse_inbound_shipments
    ADD COLUMN IF NOT EXISTS closed_by   UUID,
    ADD COLUMN IF NOT EXISTS closed_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS close_note  VARCHAR(500),
    ADD COLUMN IF NOT EXISTS closed_with_discrepancy BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE whse_receipt_events (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    shipment_id     UUID NOT NULL REFERENCES whse_inbound_shipments (id),
    line_id         UUID NOT NULL REFERENCES whse_inbound_shipment_lines (id),
    client_id       UUID NOT NULL REFERENCES whse_clients (id),
    item_id         UUID NOT NULL REFERENCES whse_items (id),
    location_id     UUID NOT NULL REFERENCES whse_locations (id),
    qty_good        NUMERIC(12,3) NOT NULL DEFAULT 0 CHECK (qty_good >= 0),
    qty_damaged     NUMERIC(12,3) NOT NULL DEFAULT 0 CHECK (qty_damaged >= 0),
    qty_held        NUMERIC(12,3) NOT NULL DEFAULT 0 CHECK (qty_held >= 0),
    reason          VARCHAR(300),
    received_by     UUID NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    idempotency_key VARCHAR(100),
    CONSTRAINT chk_whse_receipt_not_empty CHECK (qty_good + qty_damaged + qty_held > 0),
    CONSTRAINT chk_whse_receipt_reason CHECK (qty_damaged + qty_held = 0 OR (reason IS NOT NULL AND length(trim(reason)) >= 5))
);
CREATE INDEX idx_whse_receipt_events_shipment ON whse_receipt_events (tenant_id, shipment_id);
CREATE INDEX idx_whse_receipt_events_held ON whse_receipt_events (tenant_id, received_at) WHERE qty_held > 0;
CREATE UNIQUE INDEX uq_whse_receipt_events_idem ON whse_receipt_events (tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE TABLE whse_receipt_inspections (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    receipt_id      UUID NOT NULL REFERENCES whse_receipt_events (id),
    outcome         VARCHAR(4) NOT NULL CHECK (outcome IN ('PASS','FAIL')),
    qty             NUMERIC(12,3) NOT NULL CHECK (qty > 0),
    reason          VARCHAR(300) NOT NULL CHECK (length(trim(reason)) >= 5),
    inspected_by    UUID NOT NULL,
    inspected_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    sole_exception  BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_whse_receipt_inspections_receipt ON whse_receipt_inspections (tenant_id, receipt_id);

CREATE OR REPLACE FUNCTION whse_receiving_rows_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only: record a further receipt or inspection instead', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_whse_receipt_events_immutable BEFORE UPDATE OR DELETE ON whse_receipt_events
    FOR EACH ROW EXECUTE FUNCTION whse_receiving_rows_immutable();
CREATE TRIGGER trg_whse_receipt_inspections_immutable BEFORE UPDATE OR DELETE ON whse_receipt_inspections
    FOR EACH ROW EXECUTE FUNCTION whse_receiving_rows_immutable();
