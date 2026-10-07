-- V323__client_tender_pricing.sql
--
-- Client-side tender pricing (ADR-004, client side): the same cost-based price schedule as the company's own tenders (V313), for a client's tender.
--   client_tender_pricing        one row per client tender: the markups (overhead, contingency, profit) and VAT treatment
--   client_tender_pricing_lines  the schedule: what each item costs, grouped by a section name
-- Parallel tables rather than shared ones, following the Part 8 decision for complianceservices; the arithmetic is shared (the tenderpricing module).
CREATE TABLE client_tender_pricing (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    client_tender_id UUID NOT NULL REFERENCES client_tenders(id),
    overhead_pct     NUMERIC(6,2) NOT NULL DEFAULT 0,
    contingency_pct  NUMERIC(6,2) NOT NULL DEFAULT 0,
    profit_pct       NUMERIC(6,2) NOT NULL DEFAULT 0,
    vat_applies      BOOLEAN NOT NULL DEFAULT TRUE,
    vat_rate_pct     NUMERIC(5,2) NOT NULL,
    notes            TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_client_tender_pricing_pcts CHECK (overhead_pct BETWEEN 0 AND 100 AND contingency_pct BETWEEN 0 AND 100 AND profit_pct BETWEEN 0 AND 100)
);
CREATE UNIQUE INDEX uq_client_tender_pricing_tender ON client_tender_pricing(client_tender_id);
CREATE INDEX idx_client_tender_pricing_tenant ON client_tender_pricing(tenant_id);

CREATE TABLE client_tender_pricing_lines (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    client_tender_id UUID NOT NULL REFERENCES client_tenders(id),
    section          VARCHAR(120) NOT NULL,
    item_ref         VARCHAR(40),
    description      VARCHAR(500) NOT NULL,
    unit             VARCHAR(30),
    quantity         NUMERIC(15,3) NOT NULL,
    unit_cost        NUMERIC(15,2) NOT NULL,
    sort_order       INTEGER NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by       UUID,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_client_tender_pricing_lines_nonneg CHECK (quantity >= 0 AND unit_cost >= 0)
);
CREATE INDEX idx_client_tender_pricing_lines_tenant ON client_tender_pricing_lines(tenant_id);
CREATE INDEX idx_client_tender_pricing_lines_tender ON client_tender_pricing_lines(client_tender_id, sort_order);
