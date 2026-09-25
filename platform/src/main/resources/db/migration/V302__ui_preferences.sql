-- src/main/resources/db/migration/V302__ui_preferences.sql
--
-- Appearance preferences for the new app shell (UI modernization, Phase 1).
--
-- Two levels:
--   tenant_ui_preferences  - the tenant's defaults and brand colour. Managed by
--                            users with SETTINGS_MANAGE.
--   user_ui_preferences    - one user's personal overrides. NULL in any column
--                            means "inherit the tenant default".
--
-- Precedence (see UiPreferencesResolver):
--   effective = user override ?? tenant default ?? system default
--   except brand_color: when tenant.brand_locked is true, the tenant value
--   always wins and user overrides are ignored.
--
-- Missing rows are normal: a tenant or user with no row gets system defaults,
-- which match how the app looks today (navy brand, light theme, full sidebar,
-- boxed container). Nothing changes visually for anyone until they choose to.
--
-- Values are constrained here rather than stored as a free-form JSON blob so
-- that invalid data cannot be written by any path, and each new preference is
-- an explicit, reviewed migration.

CREATE TABLE tenant_ui_preferences (
    tenant_id          UUID         NOT NULL,
    brand_color        VARCHAR(20)  NOT NULL DEFAULT 'NAVY',
    brand_locked       BOOLEAN      NOT NULL DEFAULT false,
    default_theme      VARCHAR(10)  NOT NULL DEFAULT 'LIGHT',
    default_sidebar    VARCHAR(10)  NOT NULL DEFAULT 'FULL',
    default_container  VARCHAR(10)  NOT NULL DEFAULT 'BOXED',
    updated_by         UUID,
    created_at         TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_tenant_ui_preferences PRIMARY KEY (tenant_id),
    CONSTRAINT fk_tenant_ui_preferences_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT ck_tenant_ui_brand     CHECK (brand_color IN ('NAVY','OCEAN','TEAL','VIOLET','EMERALD','CHARCOAL')),
    CONSTRAINT ck_tenant_ui_theme     CHECK (default_theme IN ('LIGHT','DARK','SYSTEM')),
    CONSTRAINT ck_tenant_ui_sidebar   CHECK (default_sidebar IN ('FULL','MINI')),
    CONSTRAINT ck_tenant_ui_container CHECK (default_container IN ('BOXED','FULL'))
);

CREATE TABLE user_ui_preferences (
    user_id         UUID         NOT NULL,
    tenant_id       UUID         NOT NULL,
    theme           VARCHAR(10),
    sidebar         VARCHAR(10),
    container       VARCHAR(10),
    brand_color     VARCHAR(20),
    -- Module keys pinned in the navigation, in display order. Replaces the
    -- browser-only localStorage pinning in ModuleLayout.
    pinned_modules  JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_user_ui_preferences PRIMARY KEY (user_id),
    CONSTRAINT fk_user_ui_preferences_user   FOREIGN KEY (user_id)   REFERENCES users(id)   ON DELETE CASCADE,
    CONSTRAINT fk_user_ui_preferences_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT ck_user_ui_brand     CHECK (brand_color IS NULL OR brand_color IN ('NAVY','OCEAN','TEAL','VIOLET','EMERALD','CHARCOAL')),
    CONSTRAINT ck_user_ui_theme     CHECK (theme IS NULL OR theme IN ('LIGHT','DARK','SYSTEM')),
    CONSTRAINT ck_user_ui_sidebar   CHECK (sidebar IS NULL OR sidebar IN ('FULL','MINI')),
    CONSTRAINT ck_user_ui_container CHECK (container IS NULL OR container IN ('BOXED','FULL')),
    CONSTRAINT ck_user_ui_pinned_is_array CHECK (jsonb_typeof(pinned_modules) = 'array')
);

CREATE INDEX idx_user_ui_preferences_tenant ON user_ui_preferences (tenant_id);

COMMENT ON TABLE tenant_ui_preferences IS
    'Tenant-wide appearance defaults and brand colour. Missing row = system defaults.';
COMMENT ON TABLE user_ui_preferences IS
    'Per-user appearance overrides. NULL columns inherit tenant defaults. '
    'Always read with tenant_id as well as user_id.';
