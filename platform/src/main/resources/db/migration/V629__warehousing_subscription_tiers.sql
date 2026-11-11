-- Warehousing packages: what each package costs and what it allows. The limits below are PROPOSED numbers (proposed = true) for the product owner to confirm;
-- they are plain data, so changing one is an UPDATE, not a release. NULL limit = unlimited.
CREATE TABLE IF NOT EXISTS whse_subscription_tiers (
    code                     VARCHAR(20) PRIMARY KEY CHECK (code = upper(code)),
    name                     VARCHAR(60)  NOT NULL,
    monthly_price            NUMERIC(12,2) CHECK (monthly_price IS NULL OR monthly_price >= 0), -- ex VAT; NULL = custom quotation
    max_warehouses           INTEGER CHECK (max_warehouses IS NULL OR max_warehouses >= 1),
    max_client_accounts      INTEGER CHECK (max_client_accounts IS NULL OR max_client_accounts >= 1),
    max_monthly_transactions INTEGER CHECK (max_monthly_transactions IS NULL OR max_monthly_transactions >= 1),
    max_webhooks             INTEGER CHECK (max_webhooks IS NULL OR max_webhooks >= 0),
    sort_order               INTEGER NOT NULL,
    proposed                 BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO whse_subscription_tiers (code, name, monthly_price, max_warehouses, max_client_accounts, max_monthly_transactions, max_webhooks, sort_order) VALUES
    ('STARTER',      'Starter',       699.00, 1,    3,    500,   0, 1),
    ('SME',          'SME',          1999.00, 2,   10,   3000,   1, 2),
    ('PROFESSIONAL', 'Professional', 4999.00, 5,   40,  15000,   5, 3),
    ('BUSINESS',     'Business',     9999.00, 15, 150,  60000,  10, 4),
    ('ENTERPRISE',   'Enterprise',      NULL, NULL, NULL, NULL, 10, 5)
ON CONFLICT (code) DO NOTHING;

-- A tenant's package. No row = no limits (how every existing tenant behaves). OFF = recorded only, REPORT = usage shown and over-limit flagged, ENFORCE = new records refused.
CREATE TABLE IF NOT EXISTS whse_tenant_subscription (
    tenant_id    UUID PRIMARY KEY,
    package_code VARCHAR(20) NOT NULL REFERENCES whse_subscription_tiers (code),
    enforcement  VARCHAR(10) NOT NULL DEFAULT 'OFF' CHECK (enforcement IN ('OFF','REPORT','ENFORCE')),
    updated_by   UUID,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Catalogue position: Operations & Logistics, paid, entry price R699 (Starter). Per-package prices are in whse_subscription_tiers.
UPDATE module_catalogue
   SET name = 'Warehousing',
       description = 'WMS, 3PL, storage, fulfilment and inventory control — internal, third-party and hybrid warehouses, batch and expiry, billing and integrations.',
       monthly_price = 699, category = 'OPERATIONS'
 WHERE key = 'warehousing';
CREATE INDEX IF NOT EXISTS idx_whse_inbound_shipments_tenant_created ON whse_inbound_shipments (tenant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_whse_outbound_orders_tenant_created ON whse_outbound_orders (tenant_id, created_at);
