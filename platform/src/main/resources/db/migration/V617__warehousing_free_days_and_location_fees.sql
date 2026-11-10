-- V617: free storage days and a fixed fee per occupied pallet/bin position.
--
-- Both are part of the versioned billing terms (so they are effective-dated, immutable and, when the rule is on, go through
-- the same second-person approval as the minimum charge). A terms version is a full snapshot: the client's own version in force
-- supplies all three values, otherwise the warehouse default's.
--
--  * free_days: stock received in the last N days (the day of receipt counts as day 1) is not charged per-unit storage.
--    Counted first-in-first-out per client, item and lot. 0 = no free days.
--  * location_fee_per_month: a flat monthly fee for each pallet/bin position the client occupies, charged by the day (over 30)
--    on top of any per-unit storage. Waived on a day when everything the client holds in that position is still inside its free days.
ALTER TABLE whse_billing_terms ADD COLUMN IF NOT EXISTS free_days INT NOT NULL DEFAULT 0 CHECK (free_days BETWEEN 0 AND 3650);
ALTER TABLE whse_billing_terms ADD COLUMN IF NOT EXISTS location_fee_per_month NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (location_fee_per_month >= 0);

-- The part of a day's quantity that was free, frozen on the accrual; amount already excludes it.
ALTER TABLE whse_storage_accruals ADD COLUMN IF NOT EXISTS free_qty NUMERIC(12,3) NOT NULL DEFAULT 0;
ALTER TABLE whse_storage_accruals ADD CONSTRAINT chk_whse_accrual_free_qty CHECK (free_qty >= 0 AND free_qty <= qty_on_hand);

-- One row per client per occupied position per day.
CREATE TABLE whse_location_accruals (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    client_id           UUID NOT NULL REFERENCES whse_clients (id),
    location_id         UUID NOT NULL REFERENCES whse_locations (id),
    accrual_date        DATE NOT NULL,
    monthly_fee         NUMERIC(12,2) NOT NULL CHECK (monthly_fee >= 0),
    days_per_month      INT NOT NULL DEFAULT 30 CHECK (days_per_month > 0),
    amount              NUMERIC(15,4) NOT NULL CHECK (amount >= 0),
    free_days_waived    BOOLEAN NOT NULL DEFAULT FALSE,
    billing_invoice_id  UUID REFERENCES whse_billing_invoices (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_whse_location_accrual UNIQUE (tenant_id, client_id, location_id, accrual_date),
    CONSTRAINT chk_whse_location_accrual_waived CHECK (NOT free_days_waived OR amount = 0)
);
CREATE INDEX idx_whse_loc_accrual_client_unbilled ON whse_location_accruals (tenant_id, client_id, accrual_date) WHERE billing_invoice_id IS NULL;
CREATE INDEX idx_whse_loc_accrual_invoice ON whse_location_accruals (billing_invoice_id) WHERE billing_invoice_id IS NOT NULL;

CREATE OR REPLACE FUNCTION whse_location_accruals_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'whse_location_accruals rows cannot be deleted';
    END IF;
    IF NEW.tenant_id <> OLD.tenant_id OR NEW.client_id <> OLD.client_id OR NEW.location_id <> OLD.location_id
       OR NEW.accrual_date <> OLD.accrual_date OR NEW.monthly_fee <> OLD.monthly_fee OR NEW.amount <> OLD.amount THEN
        RAISE EXCEPTION 'whse_location_accruals figures are immutable';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_whse_location_accruals_guard BEFORE UPDATE OR DELETE ON whse_location_accruals
    FOR EACH ROW EXECUTE FUNCTION whse_location_accruals_guard();

-- Part of the invoice's storage fee that is pallet/bin fees, so the invoice can show it as its own line.
ALTER TABLE whse_billing_invoices ADD COLUMN IF NOT EXISTS storage_location_fees NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (storage_location_fees >= 0);

-- Requests for a terms change may now carry any of the three values; omitted ones carry forward from the version in force.
ALTER TABLE whse_tariff_requests ADD COLUMN IF NOT EXISTS free_days INT CHECK (free_days IS NULL OR free_days BETWEEN 0 AND 3650);
ALTER TABLE whse_tariff_requests ADD COLUMN IF NOT EXISTS location_fee_per_month NUMERIC(12,2) CHECK (location_fee_per_month IS NULL OR location_fee_per_month >= 0);
ALTER TABLE whse_tariff_requests DROP CONSTRAINT IF EXISTS chk_whse_tariff_req_shape;
ALTER TABLE whse_tariff_requests ADD CONSTRAINT chk_whse_tariff_req_shape CHECK (
    (kind = 'TARIFF' AND monthly_rate IS NOT NULL AND minimum_storage_charge IS NULL AND free_days IS NULL
        AND location_fee_per_month IS NULL AND (item_id IS NULL OR client_id IS NOT NULL))
 OR (kind = 'TERMS' AND monthly_rate IS NULL AND item_id IS NULL
        AND (minimum_storage_charge IS NOT NULL OR free_days IS NOT NULL OR location_fee_per_month IS NOT NULL)));
