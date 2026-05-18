-- src/main/resources/db/migration/V5__create_billing_tables.sql

-- =====================================================
-- PLANS
-- Defines the available subscription tiers.
-- Seeded with initial data at the bottom of this file.
-- =====================================================
CREATE TABLE plans (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    name                    VARCHAR(50) NOT NULL,
    display_name            VARCHAR(100) NOT NULL,
    description             TEXT,
    price_cents             INTEGER     NOT NULL,
    billing_period          VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    max_users               INTEGER     NOT NULL DEFAULT 5,
    included_module_count   INTEGER     NOT NULL DEFAULT 1,
    features                JSONB       NOT NULL DEFAULT '{}',
    is_active               BOOLEAN     NOT NULL DEFAULT true,
    sort_order              INTEGER     NOT NULL DEFAULT 0,
    created_at              TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT pk_plans PRIMARY KEY (id),
    CONSTRAINT uq_plans_name UNIQUE (name),
    CONSTRAINT chk_plans_billing_period
        CHECK (billing_period IN ('MONTHLY', 'ANNUAL')),
    CONSTRAINT chk_plans_price CHECK (price_cents >= 0)
);

-- =====================================================
-- PLAN MODULES
-- Which modules are included in which plans.
-- Enterprise gets all → insert a row per module.
-- =====================================================
CREATE TABLE plan_modules (
    plan_id     UUID        NOT NULL,
    module_key  VARCHAR(50) NOT NULL,

    CONSTRAINT pk_plan_modules PRIMARY KEY (plan_id, module_key),
    CONSTRAINT fk_pm_plan FOREIGN KEY (plan_id)
        REFERENCES plans(id) ON DELETE CASCADE
);

-- =====================================================
-- SUBSCRIPTIONS
-- Links a tenant to a plan with full lifecycle tracking.
-- =====================================================
CREATE TABLE subscriptions (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID        NOT NULL,
    plan_id                 UUID        NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'PILOT',
    pilot_ends_at           TIMESTAMP,
    current_period_start    TIMESTAMP   NOT NULL DEFAULT now(),
    current_period_end      TIMESTAMP   NOT NULL,
    cancelled_at            TIMESTAMP,
    cancellation_reason     TEXT,
    created_at              TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP   NOT NULL DEFAULT now(),
    version                 BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_subscriptions PRIMARY KEY (id),
    -- WHY this constraint? One active subscription per tenant at a time.
    -- You can't be on ESSENTIAL and PROFESSIONAL simultaneously.
    CONSTRAINT uq_subscriptions_tenant
        UNIQUE (tenant_id),
    CONSTRAINT fk_sub_plan FOREIGN KEY (plan_id)
        REFERENCES plans(id),
    CONSTRAINT chk_sub_status CHECK (
        status IN ('PILOT','ACTIVE','PAST_DUE','SUSPENDED','CANCELLED')
    )
);

CREATE INDEX idx_subscriptions_tenant_id ON subscriptions(tenant_id);
CREATE INDEX idx_subscriptions_status    ON subscriptions(status);
CREATE INDEX idx_subscriptions_pilot_ends ON subscriptions(pilot_ends_at)
    WHERE pilot_ends_at IS NOT NULL;
-- WHY partial index? Only PILOT subscriptions have pilot_ends_at set.
-- This index is used by the scheduler that expires pilots.

-- =====================================================
-- MODULE SUBSCRIPTIONS
-- Industry module add-ons per tenant.
-- =====================================================
CREATE TABLE module_subscriptions (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    module_key      VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    price_cents     INTEGER     NOT NULL,
    activated_at    TIMESTAMP   NOT NULL DEFAULT now(),
    cancelled_at    TIMESTAMP,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    version         BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_module_subs PRIMARY KEY (id),
    -- WHY? Can't subscribe to the same module twice
    CONSTRAINT uq_module_subs_tenant_module
        UNIQUE (tenant_id, module_key),
    CONSTRAINT chk_module_status
        CHECK (status IN ('ACTIVE', 'CANCELLED'))
);

CREATE INDEX idx_module_subs_tenant_id  ON module_subscriptions(tenant_id);
CREATE INDEX idx_module_subs_module_key ON module_subscriptions(module_key);

-- =====================================================
-- SEED DATA — PLANS
-- =====================================================
INSERT INTO plans (
    id, name, display_name, description,
    price_cents, max_users, included_module_count,
    features, sort_order
) VALUES
(
    gen_random_uuid(),
    'ESSENTIAL',
    'Essential',
    'Perfect for small teams getting started with HandyFlow.',
    59900,  -- R599/month
    5,
    1,
    '{
        "crm": true,
        "invoicing": true,
        "reporting": "basic",
        "api_access": false,
        "custom_roles": false,
        "max_customers": 500,
        "max_invoices_per_month": 50,
        "email_support": true,
        "priority_support": false
    }',
    1
),
(
    gen_random_uuid(),
    'PROFESSIONAL',
    'Professional',
    'For growing businesses that need advanced features and more users.',
    129900, -- R1299/month
    20,
    2,
    '{
        "crm": true,
        "invoicing": true,
        "reporting": "advanced",
        "api_access": true,
        "custom_roles": true,
        "max_customers": -1,
        "max_invoices_per_month": -1,
        "email_support": true,
        "priority_support": true,
        "dashboards": true
    }',
    2
),
(
    gen_random_uuid(),
    'ENTERPRISE',
    'Enterprise',
    'Unlimited users, all modules included, dedicated support and SLA.',
    299900, -- R2999/month
    -1,     -- unlimited
    -1,     -- all modules
    '{
        "crm": true,
        "invoicing": true,
        "reporting": "full",
        "api_access": true,
        "custom_roles": true,
        "max_customers": -1,
        "max_invoices_per_month": -1,
        "email_support": true,
        "priority_support": true,
        "dashboards": true,
        "dedicated_support": true,
        "sla_guarantee": true,
        "custom_integrations": true
    }',
    3
);

-- =====================================================
-- SEED DATA — ENTERPRISE GETS ALL MODULES
-- =====================================================
INSERT INTO plan_modules (plan_id, module_key)
SELECT p.id, m.module_key
FROM plans p
CROSS JOIN (
    VALUES
        ('security'),
        ('earthmoving'),
        ('clinic'),
        ('property'),
        ('fuel'),
        ('creative')
) AS m(module_key)
WHERE p.name = 'ENTERPRISE';