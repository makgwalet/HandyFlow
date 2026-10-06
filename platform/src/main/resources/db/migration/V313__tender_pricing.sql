-- V313__tender_pricing.sql
--
-- Tender pricing (ADR-004): a cost-based price schedule for the tenant's own tenders.
--   tender_pricing        one row per tender: the markups (overhead, contingency, profit) and VAT treatment
--   tender_pricing_lines  the schedule: what each item costs us (quantity x unit cost), grouped by a section name
-- VAT rate is stored on the pricing row when it is first created (the rate in force then, from VatRateProvider), so a priced tender is reproducible if the rate changes later.
-- Amounts are never stored: they are computed from lines and settings, so there is nothing to drift out of step. The submission snapshot freezes the computed result.
CREATE TABLE tender_pricing (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    tender_id      UUID NOT NULL REFERENCES tenders(id),
    overhead_pct   NUMERIC(6,2) NOT NULL DEFAULT 0,
    contingency_pct NUMERIC(6,2) NOT NULL DEFAULT 0,
    profit_pct     NUMERIC(6,2) NOT NULL DEFAULT 0,
    vat_applies    BOOLEAN NOT NULL DEFAULT TRUE,
    vat_rate_pct   NUMERIC(5,2) NOT NULL,
    notes          TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by     UUID,
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_tender_pricing_pcts CHECK (overhead_pct BETWEEN 0 AND 100 AND contingency_pct BETWEEN 0 AND 100 AND profit_pct BETWEEN 0 AND 100)
);
CREATE UNIQUE INDEX uq_tender_pricing_tender ON tender_pricing(tender_id);
CREATE INDEX idx_tender_pricing_tenant ON tender_pricing(tenant_id);

CREATE TABLE tender_pricing_lines (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    tender_id    UUID NOT NULL REFERENCES tenders(id),
    section      VARCHAR(120) NOT NULL,
    item_ref     VARCHAR(40),
    description  VARCHAR(500) NOT NULL,
    unit         VARCHAR(30),
    quantity     NUMERIC(15,3) NOT NULL,
    unit_cost    NUMERIC(15,2) NOT NULL,
    sort_order   INTEGER NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by   UUID,
    version      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_tender_pricing_lines_nonneg CHECK (quantity >= 0 AND unit_cost >= 0)
);
CREATE INDEX idx_tender_pricing_lines_tenant ON tender_pricing_lines(tenant_id);
CREATE INDEX idx_tender_pricing_lines_tender ON tender_pricing_lines(tender_id, sort_order);
