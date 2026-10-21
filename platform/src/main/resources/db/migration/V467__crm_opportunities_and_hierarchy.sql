-- V467: CRM opportunities (many deals per company, each with its own stage) and company hierarchy (parent / branch link).

CREATE TABLE crm_opportunities (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID           NOT NULL,
    customer_id         UUID           NOT NULL REFERENCES customers (id),
    name                VARCHAR(255)   NOT NULL,
    stage               VARCHAR(20)    NOT NULL,
    amount              NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (amount >= 0),
    expected_close_date DATE,
    owner_id            UUID,
    lost_reason         VARCHAR(30),
    lost_note           VARCHAR(500),
    notes               TEXT,
    closed_at           TIMESTAMPTZ,
    created_by          UUID,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT chk_crm_opportunities_stage CHECK (stage IN ('PROSPECTING', 'QUALIFIED', 'PROPOSAL', 'NEGOTIATION', 'WON', 'LOST')),
    CONSTRAINT chk_crm_opportunities_lost CHECK (stage = 'LOST' OR (lost_reason IS NULL AND lost_note IS NULL))
);
CREATE INDEX idx_crm_opportunities_tenant ON crm_opportunities (tenant_id, stage) WHERE deleted_at IS NULL;
CREATE INDEX idx_crm_opportunities_customer ON crm_opportunities (tenant_id, customer_id) WHERE deleted_at IS NULL;

-- A company can sit under another company (head office -> branch). Depth and loops are checked by the application.
ALTER TABLE customers ADD COLUMN parent_customer_id UUID REFERENCES customers (id);
ALTER TABLE customers ADD CONSTRAINT chk_customers_not_own_parent CHECK (parent_customer_id IS NULL OR parent_customer_id <> id);
CREATE INDEX idx_customers_parent ON customers (tenant_id, parent_customer_id) WHERE parent_customer_id IS NOT NULL AND deleted_at IS NULL;
