-- RENAME to the next free Flyway version AFTER the Chunk 7 webhooks migration.
-- Same base-column ASSUMPTION as earlier chunks (id, tenant_id, created_at, updated_at, version) — align with AggregateRoot.

CREATE TABLE debtcollection_collector_profiles (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID          NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ,
    version              BIGINT        NOT NULL DEFAULT 0,
    user_id              UUID          NOT NULL,
    user_name            VARCHAR(255)  NOT NULL,
    team                 VARCHAR(100),
    tier                 VARCHAR(10)   NOT NULL,
    max_open_cases       INT           CHECK (max_open_cases IS NULL OR max_open_cases >= 0),
    max_case_value       NUMERIC(15,2) CHECK (max_case_value IS NULL OR max_case_value > 0),
    max_portfolio_value  NUMERIC(15,2) CHECK (max_portfolio_value IS NULL OR max_portfolio_value > 0),
    away_until           DATE,
    active               BOOLEAN       NOT NULL DEFAULT TRUE,
    last_assigned_at     TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_dc_collector_user ON debtcollection_collector_profiles (tenant_id, user_id);

CREATE TABLE debtcollection_collector_skills (
    profile_id UUID        NOT NULL REFERENCES debtcollection_collector_profiles (id),
    skill      VARCHAR(60) NOT NULL,
    PRIMARY KEY (profile_id, skill)
);

CREATE TABLE debtcollection_allocation_rules (
    id              UUID PRIMARY KEY,
    tenant_id       UUID          NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ,
    version         BIGINT        NOT NULL DEFAULT 0,
    name            VARCHAR(100)  NOT NULL,
    rule_order      INT           NOT NULL CHECK (rule_order >= 0),
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    min_balance     NUMERIC(15,2),
    max_balance     NUMERIC(15,2),
    min_age_days    INT,
    max_age_days    INT,
    statuses_csv    VARCHAR(255),
    required_team   VARCHAR(100),
    required_skill  VARCHAR(60),
    min_tier        VARCHAR(10),
    strategy        VARCHAR(15)
);
CREATE INDEX ix_dc_alloc_rules_tenant ON debtcollection_allocation_rules (tenant_id, active, rule_order);

CREATE TABLE debtcollection_allocation_settings (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID        NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ,
    version                 BIGINT      NOT NULL DEFAULT 0,
    enabled                 BOOLEAN     NOT NULL DEFAULT FALSE,
    default_strategy        VARCHAR(15) NOT NULL,
    reallocate_unavailable  BOOLEAN     NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_dc_alloc_settings_tenant ON debtcollection_allocation_settings (tenant_id);
