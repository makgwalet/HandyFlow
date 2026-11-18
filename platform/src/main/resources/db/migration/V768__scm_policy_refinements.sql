-- SCM policy refinements: price tolerance per catalogue category, VAT recoverability per supplier,
-- exchange rates with a base currency, and a per-approver cap on price-increase approvals.

ALTER TABLE sc_procurement_policy ADD COLUMN IF NOT EXISTS base_currency VARCHAR(3) NOT NULL DEFAULT 'ZAR';

-- 1. Price-increase tolerance per catalogue category (overrides the company tolerance).
CREATE TABLE IF NOT EXISTS sc_category_price_tolerance (
    tenant_id       UUID          NOT NULL,
    category_id     UUID          NOT NULL,
    tolerance_pct   NUMERIC(5,2)  NOT NULL,
    updated_by      UUID,
    updated_by_name VARCHAR(255),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, category_id),
    CONSTRAINT chk_sc_cat_tol CHECK (tolerance_pct >= 0 AND tolerance_pct <= 100)
);

-- 2. VAT recoverability per supplier (overrides the company percentage; e.g. a non-vendor supplier recovers 0 %).
CREATE TABLE IF NOT EXISTS sc_supplier_vat (
    tenant_id           UUID          NOT NULL,
    supplier_id         UUID          NOT NULL,
    vat_recoverable_pct NUMERIC(5,2)  NOT NULL,
    note                VARCHAR(255),
    updated_by          UUID,
    updated_by_name     VARCHAR(255),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, supplier_id),
    CONSTRAINT chk_sc_sup_vat CHECK (vat_recoverable_pct >= 0 AND vat_recoverable_pct <= 100)
);

-- 3. Exchange rates: 1 unit of `currency` = `rate` units of the company's base currency, from `effective_from`.
CREATE TABLE IF NOT EXISTS sc_exchange_rates (
    id              UUID PRIMARY KEY,
    tenant_id       UUID           NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    rate            NUMERIC(18,8)  NOT NULL,
    effective_from  DATE           NOT NULL,
    created_by      UUID,
    created_by_name VARCHAR(255),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_sc_fx_rate CHECK (rate > 0),
    CONSTRAINT uq_sc_fx UNIQUE (tenant_id, currency, effective_from)
);
CREATE INDEX IF NOT EXISTS idx_sc_fx_lookup ON sc_exchange_rates (tenant_id, currency, effective_from DESC);

-- Rate to the base currency on a date: 1 for the base currency (or no currency); otherwise the latest rate on or before
-- the date; NULL when none exists (callers must treat NULL as "no rate", never as 1, where money is committed).
CREATE OR REPLACE FUNCTION sc_fx_rate(p_tenant UUID, p_currency VARCHAR, p_date DATE) RETURNS NUMERIC AS $$
    SELECT CASE
        WHEN p_currency IS NULL OR btrim(p_currency) = ''
             OR upper(p_currency) = COALESCE((SELECT base_currency FROM sc_procurement_policy WHERE tenant_id = p_tenant), 'ZAR') THEN 1
        ELSE (SELECT rate FROM sc_exchange_rates
               WHERE tenant_id = p_tenant AND currency = upper(p_currency) AND effective_from <= p_date
               ORDER BY effective_from DESC LIMIT 1)
    END
$$ LANGUAGE SQL STABLE;

-- Per-approver cap on how large a price increase (extra cost, qty x increase) a person may approve.
ALTER TABLE sc_approval_limits DROP CONSTRAINT IF EXISTS chk_sc_limit_doc;
ALTER TABLE sc_approval_limits ADD CONSTRAINT chk_sc_limit_doc CHECK (document_type IN
    ('*','REQUISITION','PURCHASE_ORDER','RFQ_AWARD','PO_AMENDMENT','SUPPLIER_INVOICE','PRICE_OVERRIDE'));
