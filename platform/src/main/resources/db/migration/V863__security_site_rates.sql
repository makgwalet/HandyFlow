-- What a client pays for a site (the contract rate). One rate applies to a site on any given day; changing the price ends the old
-- rate and adds a new one, so past invoices can always be explained. Rates are never deleted.
--   HOURLY        - cents per guard hour
--   MONTHLY_FIXED - cents per month, whatever the hours
-- Amounts are whole cents (like guard pay in security_guards.hourly_rate_cents). VAT is recorded as a flag only: vat_inclusive says
-- whether the amount already contains VAT. Effective dates are calendar days in the company's time zone, both ends inclusive.
CREATE TABLE IF NOT EXISTS security_site_rates (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL REFERENCES tenants(id),
    site_id        UUID         NOT NULL REFERENCES security_sites(id),
    rate_type      VARCHAR(20)  NOT NULL,
    amount_cents   BIGINT       NOT NULL,
    currency       CHAR(3)      NOT NULL DEFAULT 'ZAR',
    vat_inclusive  BOOLEAN      NOT NULL DEFAULT FALSE,
    effective_from DATE         NOT NULL,
    effective_to   DATE,
    note           VARCHAR(500),
    created_by     UUID,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ended_by       UUID,
    ended_at       TIMESTAMPTZ,
    CONSTRAINT pk_security_site_rates PRIMARY KEY (id),
    CONSTRAINT chk_site_rates_type CHECK (rate_type IN ('HOURLY', 'MONTHLY_FIXED')),
    CONSTRAINT chk_site_rates_amount CHECK (amount_cents > 0),
    CONSTRAINT chk_site_rates_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
-- at most one open-ended rate per site
CREATE UNIQUE INDEX IF NOT EXISTS uq_security_site_rates_open ON security_site_rates (tenant_id, site_id) WHERE effective_to IS NULL;
CREATE INDEX IF NOT EXISTS idx_security_site_rates_site ON security_site_rates (tenant_id, site_id, effective_from);
COMMENT ON TABLE security_site_rates IS 'Contract rate charged to the client for a site. Never deleted; end the old rate and add a new one.';
