-- V256__marketing_forms_topics.sql  (rename to your next Flyway version)

CREATE TABLE IF NOT EXISTS mkt_topics (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(300),
    visible     BOOLEAN      NOT NULL DEFAULT true,   -- shown on the public preference page
    created_by  UUID,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_mkt_topics_name ON mkt_topics (tenant_id, lower(name)) WHERE deleted_at IS NULL;

-- Opt-out model: no row = subscribed. A row with subscribed = false is an explicit opt-out.
CREATE TABLE IF NOT EXISTS mkt_contact_topic_prefs (
    preference_id UUID        NOT NULL,
    topic_id      UUID        NOT NULL REFERENCES mkt_topics (id),
    tenant_id     UUID        NOT NULL,
    subscribed    BOOLEAN     NOT NULL,
    source        VARCHAR(30) NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (preference_id, topic_id)
);
CREATE INDEX IF NOT EXISTS idx_mkt_ctp_topic ON mkt_contact_topic_prefs (topic_id) WHERE subscribed = false;

CREATE TABLE IF NOT EXISTS mkt_forms (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    slug            VARCHAR(20)  NOT NULL UNIQUE,
    name            VARCHAR(150) NOT NULL,
    title           VARCHAR(150) NOT NULL,
    description     TEXT,
    button_label    VARCHAR(40)  NOT NULL DEFAULT 'Subscribe',
    consent_text    TEXT         NOT NULL,
    success_message VARCHAR(300),
    collect_name    BOOLEAN      NOT NULL DEFAULT true,
    topic_ids       JSONB        NOT NULL DEFAULT '[]',
    status          VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | DISABLED
    created_by      UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_mkt_forms_tenant ON mkt_forms (tenant_id) WHERE deleted_at IS NULL;

-- One row per submission awaiting (or having received) confirmation. Only a hash of the token is stored.
CREATE TABLE IF NOT EXISTS mkt_optin_requests (
    id           UUID PRIMARY KEY,
    tenant_id    UUID         NOT NULL,
    form_id      UUID         NOT NULL,
    email        VARCHAR(320) NOT NULL,
    name         VARCHAR(150),
    token_hash   VARCHAR(64)  NOT NULL UNIQUE,
    topic_ids    JSONB        NOT NULL DEFAULT '[]',   -- topics the person left ticked
    consent_text TEXT         NOT NULL,                -- exact wording shown at submit time
    ip           VARCHAR(64),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ  NOT NULL,
    confirmed_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_mkt_optin_form  ON mkt_optin_requests (form_id);
CREATE INDEX IF NOT EXISTS idx_mkt_optin_email ON mkt_optin_requests (tenant_id, email, created_at DESC);

ALTER TABLE mkt_campaigns ADD COLUMN IF NOT EXISTS topic_id UUID;
CREATE INDEX IF NOT EXISTS idx_mkt_campaigns_topic ON mkt_campaigns (topic_id) WHERE topic_id IS NOT NULL;