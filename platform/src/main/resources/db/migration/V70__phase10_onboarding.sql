-- V70__phase10_onboarding.sql
-- Phase 10: Tenant onboarding assistance tracking

CREATE TABLE IF NOT EXISTS admin_onboarding_sessions (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL,
    tenant_slug     VARCHAR(100) NOT NULL,
    tenant_name     VARCHAR(200) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS'
        CHECK (status IN ('IN_PROGRESS','COMPLETED','ABANDONED')),
    -- Checklist flags
    company_seeded  BOOLEAN NOT NULL DEFAULT false,
    users_imported  BOOLEAN NOT NULL DEFAULT false,
    modules_enabled BOOLEAN NOT NULL DEFAULT false,
    data_seeded     BOOLEAN NOT NULL DEFAULT false,
    welcome_sent    BOOLEAN NOT NULL DEFAULT false,
    -- Metadata
    admin_id        UUID         NOT NULL,
    admin_email     VARCHAR(200),
    notes           TEXT,
    users_imported_count  INT DEFAULT 0,
    modules_enabled_list  TEXT[],
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMP,
    CONSTRAINT pk_admin_onboarding PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_onboarding_tenant ON admin_onboarding_sessions(tenant_id);
CREATE INDEX IF NOT EXISTS idx_onboarding_status ON admin_onboarding_sessions(status);
