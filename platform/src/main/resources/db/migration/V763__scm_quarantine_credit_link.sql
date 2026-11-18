-- Failed-inspection stock is quarantined (moved out of the receiving location into a Quarantine location) until it is
-- released back, returned to the supplier, or written off. Supplier credit notes are handed to Accounts Payable.

-- 1. A tenant's quarantine location. It is created on first use; availability checks leave it out.
ALTER TABLE sc_stock_locations ADD COLUMN IF NOT EXISTS is_quarantine BOOLEAN NOT NULL DEFAULT false;
CREATE UNIQUE INDEX IF NOT EXISTS uq_sc_quarantine_location_per_tenant ON sc_stock_locations (tenant_id) WHERE is_quarantine;

-- 2. What is held, why, and how it left.
CREATE TABLE IF NOT EXISTS sc_quarantine_holds (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID          NOT NULL,
    inspection_id           UUID          NOT NULL,
    goods_receipt_id        UUID          NOT NULL,
    catalogue_item_id       UUID          NOT NULL,
    item_name               VARCHAR(255)  NOT NULL,
    source_location_id      UUID          NOT NULL,
    quarantine_location_id  UUID          NOT NULL,
    qty                     NUMERIC(12,3) NOT NULL CHECK (qty > 0),
    qty_released            NUMERIC(12,3) NOT NULL DEFAULT 0 CHECK (qty_released >= 0),
    qty_returned            NUMERIC(12,3) NOT NULL DEFAULT 0 CHECK (qty_returned >= 0),
    qty_written_off         NUMERIC(12,3) NOT NULL DEFAULT 0 CHECK (qty_written_off >= 0),
    unit_cost               NUMERIC(15,2) NOT NULL DEFAULT 0,
    defect_code             VARCHAR(40),
    status                  VARCHAR(8)    NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED')),
    created_by              UUID,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_sc_hold_not_overdrawn CHECK (qty_released + qty_returned + qty_written_off <= qty)
);
CREATE INDEX IF NOT EXISTS idx_sc_holds_open ON sc_quarantine_holds (tenant_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_sc_holds_inspection ON sc_quarantine_holds (tenant_id, inspection_id);

-- 3. The credit note a return or claim produced in Accounts Payable.
ALTER TABLE sc_supplier_returns ADD COLUMN IF NOT EXISTS ap_credit_note_id UUID;

-- 4. Stock movement types used by quarantine and returns (RETURN_OUT already exists; QUARANTINE moves are TRANSFER_IN/OUT).
--    Nothing new is needed in chk_sc_stock_movement_type.
