-- V762: supplier contracts, returns and claims, quality inspection, RFQ reminder marker.

-- ── RFQ reminders: one reminder per invitation ──────────────────────────────────────────────────────────────────────────────────
ALTER TABLE sc_rfq_invitations ADD COLUMN IF NOT EXISTS reminder_sent_at TIMESTAMPTZ;

-- ── Supplier contracts ──────────────────────────────────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS sc_contracts (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID          NOT NULL,
    contract_number      VARCHAR(20)   NOT NULL,
    supplier_id          UUID          NOT NULL,
    supplier_name        VARCHAR(255)  NOT NULL,
    title                VARCHAR(200)  NOT NULL,
    status               VARCHAR(12)   NOT NULL CHECK (status IN ('DRAFT','ACTIVE','EXPIRED','TERMINATED')),
    start_date           DATE          NOT NULL,
    end_date             DATE          NOT NULL,
    currency             VARCHAR(3)    NOT NULL DEFAULT 'ZAR',
    value_cap            NUMERIC(15,2),
    payment_terms_days   INT,
    auto_renew           BOOLEAN       NOT NULL DEFAULT FALSE,
    renewal_notice_days  INT           NOT NULL DEFAULT 60,
    renewal_notice_sent_at TIMESTAMPTZ,
    document_id          UUID,
    notes                VARCHAR(2000),
    terminated_reason    VARCHAR(500),
    created_by           UUID,
    created_by_name      VARCHAR(255),
    activated_by         UUID,
    activated_by_name    VARCHAR(255),
    activated_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_sc_contract_number UNIQUE (tenant_id, contract_number),
    CONSTRAINT chk_sc_contract_dates CHECK (end_date >= start_date),
    CONSTRAINT chk_sc_contract_notice CHECK (renewal_notice_days BETWEEN 0 AND 365)
);
CREATE INDEX IF NOT EXISTS idx_sc_contracts_supplier ON sc_contracts (tenant_id, supplier_id, status);
CREATE INDEX IF NOT EXISTS idx_sc_contracts_end ON sc_contracts (end_date) WHERE status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS sc_contract_lines (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID          NOT NULL,
    contract_id        UUID          NOT NULL REFERENCES sc_contracts(id) ON DELETE CASCADE,
    catalogue_item_id  UUID,
    description        VARCHAR(255)  NOT NULL,
    agreed_unit_price  NUMERIC(15,2) NOT NULL CHECK (agreed_unit_price >= 0),
    min_qty            NUMERIC(12,3),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_sc_contract_lines_contract ON sc_contract_lines (contract_id);
CREATE INDEX IF NOT EXISTS idx_sc_contract_lines_item ON sc_contract_lines (tenant_id, catalogue_item_id);

-- ── Returns and claims ──────────────────────────────────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS sc_supplier_returns (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID          NOT NULL,
    return_number      VARCHAR(20)   NOT NULL,
    kind               VARCHAR(8)    NOT NULL CHECK (kind IN ('RETURN','CLAIM')),
    reason             VARCHAR(16)   NOT NULL CHECK (reason IN ('DAMAGED','WRONG_ITEM','SHORT_SUPPLY','QUALITY','OVERCHARGE','OTHER')),
    status             VARCHAR(12)   NOT NULL CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','DISPATCHED','CREDITED','REJECTED','CANCELLED')),
    supplier_id        UUID          NOT NULL,
    supplier_name      VARCHAR(255)  NOT NULL,
    purchase_order_id  UUID,
    goods_receipt_id   UUID,
    inspection_id      UUID,
    from_location_id   UUID,
    expected_credit    NUMERIC(15,2) NOT NULL DEFAULT 0,
    credit_note_ref    VARCHAR(100),
    credit_amount      NUMERIC(15,2),
    notes              VARCHAR(2000),
    status_reason      VARCHAR(500),
    created_by         UUID,
    created_by_name    VARCHAR(255),
    submitted_at       TIMESTAMPTZ,
    approved_by        UUID,
    approved_by_name   VARCHAR(255),
    approved_at        TIMESTAMPTZ,
    dispatched_at      TIMESTAMPTZ,
    credited_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_sc_return_number UNIQUE (tenant_id, return_number)
);
CREATE INDEX IF NOT EXISTS idx_sc_returns_supplier ON sc_supplier_returns (tenant_id, supplier_id, status);
CREATE INDEX IF NOT EXISTS idx_sc_returns_receipt ON sc_supplier_returns (tenant_id, goods_receipt_id);

CREATE TABLE IF NOT EXISTS sc_supplier_return_lines (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID          NOT NULL,
    return_id          UUID          NOT NULL REFERENCES sc_supplier_returns(id) ON DELETE CASCADE,
    gr_line_id         UUID,
    po_line_id         UUID,
    catalogue_item_id  UUID,
    item_name          VARCHAR(255)  NOT NULL,
    qty                NUMERIC(12,3) NOT NULL CHECK (qty > 0),
    unit_cost          NUMERIC(15,2) NOT NULL CHECK (unit_cost >= 0),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_sc_return_lines_return ON sc_supplier_return_lines (return_id);
CREATE INDEX IF NOT EXISTS idx_sc_return_lines_grline ON sc_supplier_return_lines (tenant_id, gr_line_id);

-- ── Quality inspection ──────────────────────────────────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS sc_quality_inspections (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID          NOT NULL,
    inspection_number  VARCHAR(20)   NOT NULL,
    goods_receipt_id   UUID          NOT NULL,
    purchase_order_id  UUID,
    supplier_id        UUID          NOT NULL,
    status             VARCHAR(10)   NOT NULL CHECK (status IN ('OPEN','COMPLETED','CANCELLED')),
    result             VARCHAR(8)    CHECK (result IN ('PASSED','PARTIAL','FAILED')),
    notes              VARCHAR(2000),
    inspected_by       UUID,
    inspected_by_name  VARCHAR(255),
    completed_at       TIMESTAMPTZ,
    return_id          UUID,
    created_by         UUID,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_sc_inspection_number UNIQUE (tenant_id, inspection_number)
);
-- one inspection in progress per receipt
CREATE UNIQUE INDEX IF NOT EXISTS uq_sc_inspection_open_per_receipt ON sc_quality_inspections (tenant_id, goods_receipt_id) WHERE status = 'OPEN';
CREATE INDEX IF NOT EXISTS idx_sc_inspections_supplier ON sc_quality_inspections (tenant_id, supplier_id, status);

CREATE TABLE IF NOT EXISTS sc_quality_inspection_lines (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID          NOT NULL,
    inspection_id      UUID          NOT NULL REFERENCES sc_quality_inspections(id) ON DELETE CASCADE,
    gr_line_id         UUID          NOT NULL,
    po_line_id         UUID,
    catalogue_item_id  UUID,
    item_name          VARCHAR(255)  NOT NULL,
    qty_accepted_on_receipt NUMERIC(12,3) NOT NULL,
    qty_inspected      NUMERIC(12,3) NOT NULL DEFAULT 0,
    qty_passed         NUMERIC(12,3) NOT NULL DEFAULT 0,
    qty_failed         NUMERIC(12,3) NOT NULL DEFAULT 0,
    defect_code        VARCHAR(40),
    notes              VARCHAR(500),
    unit_cost          NUMERIC(15,2) NOT NULL DEFAULT 0,
    CONSTRAINT chk_sc_inspection_line_qty CHECK (qty_passed >= 0 AND qty_failed >= 0 AND qty_inspected = qty_passed + qty_failed
                                                 AND qty_inspected <= qty_accepted_on_receipt)
);
CREATE INDEX IF NOT EXISTS idx_sc_inspection_lines_inspection ON sc_quality_inspection_lines (inspection_id);
