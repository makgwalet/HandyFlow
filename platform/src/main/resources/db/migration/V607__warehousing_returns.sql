-- V607: Warehousing returns (RMA). A client's customer sends goods back; each line is received into stock as restocked (AVAILABLE),
-- held for inspection (QUARANTINE) or damaged (DAMAGED) with a RETURN movement. A return linked to a shipped order can never bring back
-- more than was shipped, counting returns already received against that order. Receipts are append-only.

CREATE TABLE whse_returns (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    rma_number        VARCHAR(30) NOT NULL,
    client_id         UUID NOT NULL REFERENCES whse_clients (id),
    outbound_order_id UUID REFERENCES whse_outbound_orders (id),
    status            VARCHAR(20) NOT NULL DEFAULT 'EXPECTED' CHECK (status IN ('EXPECTED','RECEIVING','CLOSED','CANCELLED')),
    reason            VARCHAR(300) NOT NULL CHECK (length(trim(reason)) >= 3),
    notes             VARCHAR(500),
    created_by        UUID NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_by         UUID,
    closed_at         TIMESTAMPTZ,
    close_note        VARCHAR(500),
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_whse_rma_number UNIQUE (tenant_id, rma_number)
);
CREATE INDEX idx_whse_returns_client ON whse_returns (tenant_id, client_id, created_at DESC);
CREATE INDEX idx_whse_returns_order ON whse_returns (tenant_id, outbound_order_id) WHERE outbound_order_id IS NOT NULL;

CREATE TABLE whse_return_lines (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    return_id     UUID NOT NULL REFERENCES whse_returns (id),
    item_id       UUID NOT NULL REFERENCES whse_items (id),
    expected_qty  NUMERIC(12,3) NOT NULL CHECK (expected_qty > 0),
    CONSTRAINT uq_whse_return_line_item UNIQUE (return_id, item_id)
);

CREATE TABLE whse_return_receipts (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    return_id       UUID NOT NULL REFERENCES whse_returns (id),
    line_id         UUID NOT NULL REFERENCES whse_return_lines (id),
    item_id         UUID NOT NULL REFERENCES whse_items (id),
    location_id     UUID NOT NULL REFERENCES whse_locations (id),
    qty_restock     NUMERIC(12,3) NOT NULL DEFAULT 0 CHECK (qty_restock >= 0),
    qty_quarantine  NUMERIC(12,3) NOT NULL DEFAULT 0 CHECK (qty_quarantine >= 0),
    qty_damaged     NUMERIC(12,3) NOT NULL DEFAULT 0 CHECK (qty_damaged >= 0),
    reason          VARCHAR(300),
    received_by     UUID NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    idempotency_key VARCHAR(100),
    CONSTRAINT chk_whse_return_receipt_not_empty CHECK (qty_restock + qty_quarantine + qty_damaged > 0),
    CONSTRAINT chk_whse_return_receipt_reason CHECK (qty_quarantine + qty_damaged = 0 OR (reason IS NOT NULL AND length(trim(reason)) >= 5))
);
CREATE INDEX idx_whse_return_receipts_line ON whse_return_receipts (tenant_id, line_id);
CREATE UNIQUE INDEX uq_whse_return_receipts_idem ON whse_return_receipts (tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE TRIGGER trg_whse_return_receipts_immutable BEFORE UPDATE OR DELETE ON whse_return_receipts
    FOR EACH ROW EXECUTE FUNCTION whse_receiving_rows_immutable();
