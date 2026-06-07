-- V69__phase9_discounts_volume.sql
-- Phase 9: Volume discounts, partnership pricing, discount code tracking

-- ── Volume discount tiers ─────────────────────────────────────────────────────
-- A volume discount automatically applies when a tenant has >= min_modules active.
-- Example: 5+ modules → 10% off all future activations
CREATE TABLE IF NOT EXISTS admin_volume_discounts (
    id              UUID          NOT NULL DEFAULT gen_random_uuid(),
    min_modules     INT           NOT NULL,  -- threshold: number of ACTIVE modules
    discount_pct    NUMERIC(5,2)  NOT NULL CHECK (discount_pct > 0 AND discount_pct <= 100),
    description     VARCHAR(200),
    active          BOOLEAN       NOT NULL DEFAULT true,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_admin_volume_discounts PRIMARY KEY (id),
    CONSTRAINT uq_volume_discount_tier UNIQUE (min_modules)
);

-- Seed default tiers
INSERT INTO admin_volume_discounts (min_modules, discount_pct, description) VALUES
    (3,  5.00,  '5% off when 3+ modules active'),
    (5,  10.00, '10% off when 5+ modules active'),
    (8,  15.00, '15% off when 8+ modules active'),
    (12, 20.00, '20% off when 12+ modules active')
ON CONFLICT (min_modules) DO NOTHING;

-- ── Partnership pricing ───────────────────────────────────────────────────────
-- A partnership agreement gives a named partner a fixed % discount on all modules
-- or on a specific module, applied automatically when they activate.
CREATE TABLE IF NOT EXISTS admin_partnerships (
    id              UUID          NOT NULL DEFAULT gen_random_uuid(),
    partner_name    VARCHAR(200)  NOT NULL,
    contact_email   VARCHAR(200),
    discount_pct    NUMERIC(5,2)  NOT NULL CHECK (discount_pct > 0 AND discount_pct <= 100),
    applies_to      VARCHAR(20)   NOT NULL DEFAULT 'ALL'
        CHECK (applies_to IN ('ALL', 'MODULE')),
    module_key      VARCHAR(50),              -- if applies_to = MODULE
    tenant_ids      UUID[],                   -- specific tenants in the partnership
    valid_from      DATE,
    valid_to        DATE,
    notes           TEXT,
    active          BOOLEAN       NOT NULL DEFAULT true,
    created_by      UUID,                     -- admin_users.id
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_admin_partnerships PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_admin_partnerships_active ON admin_partnerships(active);

-- ── Discount code redemptions (audit trail) ───────────────────────────────────
-- Records every time a discount code is applied to a module activation.
CREATE TABLE IF NOT EXISTS admin_discount_redemptions (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    discount_id     UUID         NOT NULL REFERENCES admin_discounts(id),
    tenant_id       UUID         NOT NULL,
    module_key      VARCHAR(50)  NOT NULL,
    discount_pct    NUMERIC(5,2),            -- % applied at time of redemption
    discount_fixed  NUMERIC(10,2),           -- or fixed amount
    original_price  NUMERIC(10,2) NOT NULL,
    final_price     NUMERIC(10,2) NOT NULL,
    redeemed_by     UUID,                    -- user_id who activated
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_admin_discount_redemptions PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_discount_redemptions_tenant   ON admin_discount_redemptions(tenant_id);
CREATE INDEX IF NOT EXISTS idx_discount_redemptions_discount ON admin_discount_redemptions(discount_id);
