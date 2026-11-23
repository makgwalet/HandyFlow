-- Single sign-on settings per company (first slice: the settings and the sign-in rule; the provider hand-off comes later).
-- No secret is stored here: the client secret lives in the server's environment and this table only names the variable (client_secret_ref).
-- "Enforce" means people with an email at one of the allowed domains must sign in through the provider instead of with a password.
-- Break-glass users are the few who can still use a password, so a broken provider cannot lock the company out.
CREATE TABLE IF NOT EXISTS tenant_sso_settings (
    tenant_id         UUID          NOT NULL REFERENCES tenants(id),
    enabled           BOOLEAN       NOT NULL DEFAULT FALSE,
    provider          VARCHAR(10)   NOT NULL DEFAULT 'OIDC',
    issuer_url        VARCHAR(500),
    client_id         VARCHAR(200),
    client_secret_ref VARCHAR(100),
    allowed_domains   TEXT[]        NOT NULL DEFAULT '{}',
    enforce           BOOLEAN       NOT NULL DEFAULT FALSE,
    updated_by        UUID,
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_tenant_sso_settings PRIMARY KEY (tenant_id),
    CONSTRAINT chk_sso_provider CHECK (provider IN ('OIDC')),
    CONSTRAINT chk_sso_domains_max CHECK (cardinality(allowed_domains) <= 20),
    CONSTRAINT chk_sso_enabled_complete CHECK (NOT enabled OR (issuer_url IS NOT NULL AND client_id IS NOT NULL AND cardinality(allowed_domains) >= 1)),
    CONSTRAINT chk_sso_enforce_enabled CHECK (NOT enforce OR enabled)
);

CREATE TABLE IF NOT EXISTS tenant_sso_break_glass (
    tenant_id  UUID        NOT NULL REFERENCES tenants(id),
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_tenant_sso_break_glass PRIMARY KEY (user_id)
);
CREATE INDEX IF NOT EXISTS idx_sso_break_glass_tenant ON tenant_sso_break_glass (tenant_id);
