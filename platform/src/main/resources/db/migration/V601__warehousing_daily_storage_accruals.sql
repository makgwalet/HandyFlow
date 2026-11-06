-- Warehousing slice 2: daily storage accruals.
-- One row per stock position per day, written at end of day. The rate is frozen on the row, so a later tariff
-- change never rewrites history, and billing_invoice_id stops a day being billed twice.
CREATE TABLE whse_storage_accruals (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    client_id           UUID NOT NULL,
    item_id             UUID NOT NULL,
    location_id         UUID NOT NULL,
    inventory_id        UUID NOT NULL REFERENCES whse_inventory (id),
    status              VARCHAR(20) NOT NULL,
    accrual_date        DATE NOT NULL,
    qty_on_hand         NUMERIC(12,3) NOT NULL CHECK (qty_on_hand > 0),
    monthly_rate        NUMERIC(12,4) NOT NULL CHECK (monthly_rate >= 0),
    rate_source         VARCHAR(10) NOT NULL CHECK (rate_source IN ('ITEM','CLIENT','PROFILE')),
    days_per_month      INT NOT NULL DEFAULT 30 CHECK (days_per_month > 0),
    amount              NUMERIC(15,4) NOT NULL CHECK (amount >= 0),
    billing_invoice_id  UUID REFERENCES whse_billing_invoices (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_whse_accrual_position_day UNIQUE (inventory_id, accrual_date)
);

CREATE INDEX idx_whse_accrual_client_unbilled
    ON whse_storage_accruals (tenant_id, client_id, accrual_date) WHERE billing_invoice_id IS NULL;
CREATE INDEX idx_whse_accrual_invoice ON whse_storage_accruals (billing_invoice_id) WHERE billing_invoice_id IS NOT NULL;

-- A billed accrual is never edited or removed; only the invoice link may be set once.
CREATE OR REPLACE FUNCTION whse_storage_accruals_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'whse_storage_accruals rows cannot be deleted';
    END IF;
    IF NEW.tenant_id <> OLD.tenant_id OR NEW.client_id <> OLD.client_id OR NEW.inventory_id <> OLD.inventory_id
       OR NEW.accrual_date <> OLD.accrual_date OR NEW.qty_on_hand <> OLD.qty_on_hand
       OR NEW.monthly_rate <> OLD.monthly_rate OR NEW.amount <> OLD.amount THEN
        RAISE EXCEPTION 'whse_storage_accruals figures are immutable';
    END IF;
    IF OLD.billing_invoice_id IS NOT NULL AND NEW.billing_invoice_id IS DISTINCT FROM OLD.billing_invoice_id THEN
        RAISE EXCEPTION 'accrual is already billed';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_whse_storage_accruals_guard
    BEFORE UPDATE OR DELETE ON whse_storage_accruals
    FOR EACH ROW EXECUTE FUNCTION whse_storage_accruals_guard();
