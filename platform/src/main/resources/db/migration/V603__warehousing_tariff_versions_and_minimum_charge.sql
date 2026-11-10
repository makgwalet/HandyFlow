-- Warehousing: effective-dated storage tariffs and a minimum storage charge.
--
-- Tariffs are append-only versions. A version applies from effective_from until a later one for the same scope replaces it;
-- the latest effective_from wins, and for the same date the most recently created. Nothing is edited or deleted, so what a
-- client was charged on any past day can always be explained. The accrual row already freezes the rate it used.
-- Scopes, most specific first: item (client_id + item_id), client (client_id), warehouse default (both null).

CREATE TABLE whse_storage_tariffs (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    client_id       UUID REFERENCES whse_clients (id),
    item_id         UUID REFERENCES whse_items (id),
    monthly_rate    NUMERIC(12,4) NOT NULL CHECK (monthly_rate >= 0),
    effective_from  DATE NOT NULL,
    note            VARCHAR(300),
    created_by      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_whse_tariff_scope CHECK (item_id IS NULL OR client_id IS NOT NULL)
);
CREATE INDEX idx_whse_tariff_lookup ON whse_storage_tariffs (tenant_id, client_id, item_id, effective_from DESC);

-- Minimum storage charge, per 30 days, for a client (or the warehouse default). Applied to each invoice in proportion to
-- the days it covers; the top-up is shown on the invoice so it is never a hidden figure.
CREATE TABLE whse_billing_terms (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    client_id               UUID REFERENCES whse_clients (id),
    minimum_storage_charge  NUMERIC(12,2) NOT NULL CHECK (minimum_storage_charge >= 0),
    effective_from          DATE NOT NULL,
    note                    VARCHAR(300),
    created_by              UUID NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_whse_terms_lookup ON whse_billing_terms (tenant_id, client_id, effective_from DESC);

CREATE OR REPLACE FUNCTION whse_versioned_rows_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% rows are versions: add a new version instead of changing or deleting one', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_whse_storage_tariffs_immutable BEFORE UPDATE OR DELETE ON whse_storage_tariffs
    FOR EACH ROW EXECUTE FUNCTION whse_versioned_rows_immutable();
CREATE TRIGGER trg_whse_billing_terms_immutable BEFORE UPDATE OR DELETE ON whse_billing_terms
    FOR EACH ROW EXECUTE FUNCTION whse_versioned_rows_immutable();

-- Accruals record which version priced them.
ALTER TABLE whse_storage_accruals ADD COLUMN IF NOT EXISTS tariff_id UUID;
ALTER TABLE whse_storage_accruals DROP CONSTRAINT IF EXISTS whse_storage_accruals_rate_source_check;
ALTER TABLE whse_storage_accruals ADD CONSTRAINT whse_storage_accruals_rate_source_check
    CHECK (rate_source IN ('ITEM','CLIENT','PROFILE','ITEM_TARIFF','CLIENT_TARIFF','DEFAULT_TARIFF'));

-- Minimum-charge top-up shown on the invoice.
ALTER TABLE whse_billing_invoices ADD COLUMN IF NOT EXISTS storage_minimum_topup NUMERIC(15,2) NOT NULL DEFAULT 0
    CHECK (storage_minimum_topup >= 0);
