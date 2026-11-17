-- Supply Chain: purchase order revisions (immutable snapshots) and controlled amendments.

ALTER TABLE sc_purchase_orders ADD COLUMN IF NOT EXISTS revision_number INT NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS sc_po_versions (
    id                UUID PRIMARY KEY,
    tenant_id         UUID          NOT NULL,
    purchase_order_id UUID          NOT NULL REFERENCES sc_purchase_orders(id) ON DELETE CASCADE,
    revision_number   INT           NOT NULL,
    amendment_id      UUID,
    reason            VARCHAR(500),
    subtotal          NUMERIC(15,2) NOT NULL,
    vat_amount        NUMERIC(15,2) NOT NULL,
    total_amount      NUMERIC(15,2) NOT NULL,
    snapshot_json     TEXT          NOT NULL,
    created_by        UUID,
    created_by_name   VARCHAR(255),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_sc_po_version UNIQUE (purchase_order_id, revision_number)
);
CREATE INDEX IF NOT EXISTS idx_sc_po_versions_po ON sc_po_versions(tenant_id, purchase_order_id, revision_number);

CREATE TABLE IF NOT EXISTS sc_po_amendments (
    id                       UUID PRIMARY KEY,
    tenant_id                UUID          NOT NULL,
    purchase_order_id        UUID          NOT NULL REFERENCES sc_purchase_orders(id) ON DELETE CASCADE,
    amendment_number         INT           NOT NULL,
    base_revision            INT           NOT NULL,
    status                   VARCHAR(20)   NOT NULL,
    material                 BOOLEAN       NOT NULL,
    reason                   VARCHAR(500)  NOT NULL,
    change_summary           VARCHAR(1000),
    new_required_by_date     DATE,
    new_deliver_to_location  UUID,
    new_notes                TEXT,
    new_terms                TEXT,
    previous_total           NUMERIC(15,2) NOT NULL,
    proposed_total           NUMERIC(15,2) NOT NULL,
    requested_by             UUID,
    requested_by_name        VARCHAR(255),
    decided_by               UUID,
    decided_by_name          VARCHAR(255),
    decided_at               TIMESTAMPTZ,
    decision_note            VARCHAR(500),
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_sc_po_amendment_status CHECK (status IN ('PENDING_APPROVAL','APPLIED','REJECTED','WITHDRAWN')),
    CONSTRAINT uq_sc_po_amendment_number UNIQUE (purchase_order_id, amendment_number)
);
CREATE INDEX IF NOT EXISTS idx_sc_po_amendments_po ON sc_po_amendments(tenant_id, purchase_order_id, status);
-- At most one open amendment per PO, even under concurrent requests.
CREATE UNIQUE INDEX IF NOT EXISTS uq_sc_po_amendment_open ON sc_po_amendments(purchase_order_id) WHERE status = 'PENDING_APPROVAL';

CREATE TABLE IF NOT EXISTS sc_po_amendment_lines (
    id                UUID PRIMARY KEY,
    tenant_id         UUID          NOT NULL,
    amendment_id      UUID          NOT NULL REFERENCES sc_po_amendments(id) ON DELETE CASCADE,
    action            VARCHAR(10)   NOT NULL,
    po_line_id        UUID,
    catalogue_item_id UUID,
    item_name         VARCHAR(255),
    supplier_sku      VARCHAR(100),
    qty_ordered       NUMERIC(12,3),
    unit_cost         NUMERIC(15,2),
    vat_rate          NUMERIC(5,2),
    CONSTRAINT chk_sc_po_amendment_line_action CHECK (action IN ('ADD','UPDATE','REMOVE'))
);
CREATE INDEX IF NOT EXISTS idx_sc_po_amend_lines ON sc_po_amendment_lines(tenant_id, amendment_id);
