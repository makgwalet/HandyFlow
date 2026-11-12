-- B07: price changes can wait for approval and keep a record of what they replaced.
-- Additive only. Existing prices stay APPROVED, so live pricing is unchanged.
ALTER TABLE fuel_prices
    ADD COLUMN approval_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN approval_request_id UUID,
    ADD COLUMN approval_reason VARCHAR(300),
    ADD COLUMN previous_price_per_litre NUMERIC(10,4),
    ADD COLUMN cost_per_litre_at_entry NUMERIC(10,4),
    ADD COLUMN decided_at TIMESTAMPTZ,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE fuel_prices
    ADD CONSTRAINT chk_fuel_prices_approval_status
    CHECK (approval_status IN ('APPROVED', 'PENDING_APPROVAL', 'REJECTED'));

-- Both off (empty) by default, so existing tenants behave exactly as before.
ALTER TABLE fuel_settings
    ADD COLUMN price_change_approval_pct NUMERIC(6,2),
    ADD COLUMN min_margin_pct NUMERIC(6,2);

ALTER TABLE fuel_settings
    ADD CONSTRAINT chk_fuel_settings_price_change_pct CHECK (price_change_approval_pct IS NULL OR price_change_approval_pct >= 0),
    ADD CONSTRAINT chk_fuel_settings_min_margin_pct CHECK (min_margin_pct IS NULL OR (min_margin_pct >= 0 AND min_margin_pct < 100));

CREATE INDEX idx_fuel_prices_pending ON fuel_prices (tenant_id) WHERE approval_status = 'PENDING_APPROVAL';
