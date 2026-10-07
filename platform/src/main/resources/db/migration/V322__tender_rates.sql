-- The company's rates library: reusable costs (materials, labour, plant, subcontract) that can be copied onto a tender's price schedule, entered by hand or imported from a
-- supplier's price list. A rate is identified by what it is (description + unit + supplier, ignoring case); importing the same list again updates the cost instead of duplicating.
CREATE TABLE tender_rates (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    category            VARCHAR(20) NOT NULL,
    item_ref            VARCHAR(40),
    description         VARCHAR(500) NOT NULL,
    unit                VARCHAR(30) NOT NULL DEFAULT '',
    unit_cost           NUMERIC(15,2) NOT NULL CHECK (unit_cost >= 0),
    supplier            VARCHAR(120) NOT NULL DEFAULT '',
    notes               VARCHAR(500),
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    previous_unit_cost  NUMERIC(15,2),
    price_changed_at    TIMESTAMPTZ,
    source              VARCHAR(10) NOT NULL DEFAULT 'MANUAL',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by          UUID,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_tender_rates ON tender_rates(tenant_id, LOWER(description), LOWER(unit), LOWER(supplier));
CREATE INDEX idx_tender_rates_tenant ON tender_rates(tenant_id, active, category);
