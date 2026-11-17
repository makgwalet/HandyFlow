-- Renumber to your next Flyway version (after chunk 7's).
CREATE TABLE IF NOT EXISTS sc_rfqs (
    id                       UUID PRIMARY KEY,
    tenant_id                UUID          NOT NULL,
    rfq_number               VARCHAR(20)   NOT NULL,
    status                   VARCHAR(12)   NOT NULL CHECK (status IN ('DRAFT','OPEN','CLOSED','AWARDED','CANCELLED')),
    title                    VARCHAR(200)  NOT NULL,
    description              TEXT,
    requisition_id           UUID,
    deliver_to_location      UUID,
    required_by_date         DATE,
    closes_at                TIMESTAMPTZ,
    currency                 VARCHAR(3)    NOT NULL DEFAULT 'ZAR',
    weight_price             INT           NOT NULL,
    weight_lead_time         INT           NOT NULL,
    weight_technical         INT           NOT NULL,
    weight_performance       INT           NOT NULL,
    weight_bbbee             INT           NOT NULL,
    sole_source_justification VARCHAR(500),
    created_by               UUID,
    created_by_name          VARCHAR(255),
    published_at             TIMESTAMPTZ,
    closed_at                TIMESTAMPTZ,
    awarded_quotation_id     UUID,
    award_reason             VARCHAR(1000),
    overrides_recommendation BOOLEAN       NOT NULL DEFAULT FALSE,
    awarded_by               UUID,
    awarded_by_name          VARCHAR(255),
    awarded_at               TIMESTAMPTZ,
    po_id                    UUID,
    cancel_reason            VARCHAR(500),
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_sc_rfq_number UNIQUE (tenant_id, rfq_number),
    CONSTRAINT chk_sc_rfq_weights CHECK (weight_price + weight_lead_time + weight_technical + weight_performance + weight_bbbee = 100)
);
CREATE INDEX IF NOT EXISTS idx_sc_rfqs_tenant ON sc_rfqs(tenant_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sc_rfqs_closing ON sc_rfqs(closes_at) WHERE status = 'OPEN';

CREATE TABLE IF NOT EXISTS sc_rfq_lines (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID          NOT NULL,
    rfq_id               UUID          NOT NULL REFERENCES sc_rfqs(id) ON DELETE CASCADE,
    line_number          INT           NOT NULL,
    requisition_line_id  UUID,
    catalogue_item_id    UUID,
    item_name            VARCHAR(255)  NOT NULL,
    description          VARCHAR(500),
    quantity             NUMERIC(12,3) NOT NULL,
    estimated_unit_cost  NUMERIC(15,2)          -- internal reference only; never sent to suppliers
);
CREATE INDEX IF NOT EXISTS idx_sc_rfq_lines ON sc_rfq_lines(tenant_id, rfq_id);

CREATE TABLE IF NOT EXISTS sc_rfq_invitations (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    rfq_id        UUID         NOT NULL REFERENCES sc_rfqs(id) ON DELETE CASCADE,
    supplier_id   UUID         NOT NULL,
    supplier_name VARCHAR(255) NOT NULL,
    contact_email VARCHAR(255),
    status        VARCHAR(12)  NOT NULL CHECK (status IN ('INVITED','RESPONDED','DECLINED')),
    decline_reason VARCHAR(500),
    emailed       BOOLEAN      NOT NULL DEFAULT FALSE,
    invited_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    responded_at  TIMESTAMPTZ,
    CONSTRAINT uq_sc_rfq_invitation UNIQUE (rfq_id, supplier_id)
);

CREATE TABLE IF NOT EXISTS sc_quotations (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID          NOT NULL,
    rfq_id             UUID          NOT NULL REFERENCES sc_rfqs(id) ON DELETE CASCADE,
    supplier_id        UUID          NOT NULL,
    supplier_name      VARCHAR(255)  NOT NULL,
    status             VARCHAR(14)   NOT NULL CHECK (status IN ('SUBMITTED','WITHDRAWN','DISQUALIFIED')),
    quote_reference    VARCHAR(100),
    received_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    valid_until        DATE,
    lead_time_days     INT           NOT NULL,
    warranty_months    INT,
    payment_terms_days INT,
    delivery_cost      NUMERIC(15,2) NOT NULL DEFAULT 0,
    other_charges      NUMERIC(15,2) NOT NULL DEFAULT 0,
    subtotal           NUMERIC(15,2) NOT NULL,       -- lines after discount, ex VAT
    landed_cost        NUMERIC(15,2) NOT NULL,       -- subtotal + delivery + other, ex VAT (what comparison uses)
    total_incl_vat     NUMERIC(15,2) NOT NULL,
    technical_score    INT CHECK (technical_score BETWEEN 0 AND 100),
    technical_notes    VARCHAR(1000),
    evaluated_by       UUID,
    evaluated_by_name  VARCHAR(255),
    status_reason      VARCHAR(500),
    notes              VARCHAR(1000),
    evidence_id        UUID,
    file_name          VARCHAR(300),
    content_type       VARCHAR(100),
    captured_by        UUID,
    captured_by_name   VARCHAR(255),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_sc_quotation_supplier UNIQUE (rfq_id, supplier_id)
);

CREATE TABLE IF NOT EXISTS sc_quotation_lines (
    id               UUID PRIMARY KEY,
    tenant_id        UUID          NOT NULL,
    quotation_id     UUID          NOT NULL REFERENCES sc_quotations(id) ON DELETE CASCADE,
    rfq_line_id      UUID          NOT NULL,
    no_bid           BOOLEAN       NOT NULL DEFAULT FALSE,
    qty_offered      NUMERIC(12,3),
    unit_price       NUMERIC(15,2),
    discount_pct     NUMERIC(5,2)  NOT NULL DEFAULT 0,
    vat_rate         NUMERIC(5,2)  NOT NULL,
    line_total       NUMERIC(15,2) NOT NULL DEFAULT 0     -- after discount, ex VAT
);
CREATE INDEX IF NOT EXISTS idx_sc_quotation_lines ON sc_quotation_lines(tenant_id, quotation_id);

CREATE TABLE IF NOT EXISTS sc_rfq_events (
    id         UUID PRIMARY KEY,
    tenant_id  UUID         NOT NULL,
    rfq_id     UUID         NOT NULL,
    event_type VARCHAR(30)  NOT NULL,
    detail     VARCHAR(1000),
    actor      UUID,
    actor_name VARCHAR(255),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_sc_rfq_events ON sc_rfq_events(tenant_id, rfq_id, created_at);