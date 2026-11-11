-- V638: generic, module-agnostic tier price book and tenant tier subscription history (COMMERCIAL-03 phase B).
-- No prices are seeded. A price is only used for billing once an administrator has approved it, so nothing
-- changes for any tenant until the commercial price catalogue is approved.

CREATE TABLE IF NOT EXISTS module_tiers (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    module_key  VARCHAR(50) NOT NULL,
    tier_code   VARCHAR(30) NOT NULL CHECK (tier_code = upper(tier_code)),
    name        VARCHAR(80) NOT NULL,
    sort_order  INTEGER     NOT NULL,
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    UNIQUE (module_key, tier_code)
);

-- Effective-dated price history. amount NULL = negotiated per tenant (Enterprise style).
CREATE TABLE IF NOT EXISTS module_tier_prices (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    module_key     VARCHAR(50) NOT NULL,
    tier_code      VARCHAR(30) NOT NULL,
    billing_period VARCHAR(10) NOT NULL CHECK (billing_period IN ('MONTHLY','ANNUAL')),
    amount         NUMERIC(12,2) CHECK (amount IS NULL OR amount >= 0),   -- ex VAT
    currency       VARCHAR(3)  NOT NULL DEFAULT 'ZAR',
    effective_from DATE        NOT NULL,
    approved       BOOLEAN     NOT NULL DEFAULT FALSE,
    approved_by    UUID,
    approved_at    TIMESTAMPTZ,
    created_by     UUID,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    note           VARCHAR(300),
    FOREIGN KEY (module_key, tier_code) REFERENCES module_tiers (module_key, tier_code),
    UNIQUE (module_key, tier_code, billing_period, effective_from),
    CHECK (approved = (approved_by IS NOT NULL AND approved_at IS NOT NULL))
);

-- A tenant's tier for a module over time. effective_to is exclusive; NULL = open-ended.
-- price_snapshot is copied when the row is created so later price changes never rewrite history.
-- NULL snapshot = unpriced: the module keeps billing at the flat catalogue price.
CREATE TABLE IF NOT EXISTS tenant_module_subscriptions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    module_key        VARCHAR(50) NOT NULL,
    tier_code         VARCHAR(30) NOT NULL,
    billing_period    VARCHAR(10) NOT NULL CHECK (billing_period IN ('MONTHLY','ANNUAL')),
    effective_from    DATE NOT NULL,
    effective_to      DATE,
    price_id          UUID REFERENCES module_tier_prices (id),
    price_snapshot    NUMERIC(12,2) CHECK (price_snapshot IS NULL OR price_snapshot >= 0),
    negotiated        BOOLEAN NOT NULL DEFAULT FALSE,
    change_kind       VARCHAR(12) NOT NULL CHECK (change_kind IN ('NEW','UPGRADE','DOWNGRADE','RENEWAL','SAME_TIER')),
    proration_amount  NUMERIC(12,2) CHECK (proration_amount IS NULL OR proration_amount >= 0),
    proration_invoiced BOOLEAN NOT NULL DEFAULT FALSE,
    cancelled         BOOLEAN NOT NULL DEFAULT FALSE,
    created_by        UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    note              VARCHAR(300),
    FOREIGN KEY (module_key, tier_code) REFERENCES module_tiers (module_key, tier_code),
    CHECK (effective_to IS NULL OR effective_to > effective_from)
);
CREATE INDEX IF NOT EXISTS idx_tms_tenant_module ON tenant_module_subscriptions (tenant_id, module_key, effective_from);

INSERT INTO module_tiers (module_key, tier_code, name, sort_order)
SELECT 'warehousing', code, name, sort_order FROM whse_subscription_tiers
ON CONFLICT (module_key, tier_code) DO NOTHING;
