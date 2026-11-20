-- ============================================================================
-- Legal / Compliance — Phase 6 (part 2): regulatory monitoring. Depends on V811. Confirm the next free Flyway version before applying.
--
-- Watch sources: public RSS/Atom feeds a tenant chooses to follow (https only, validated in the application). Watch items: what the feeds publish, kept once per
-- (source, guid). An item is only ever triaged by a person (NEW -> RELEVANT / NOT_RELEVANT); nothing here creates a regulatory change or an obligation by itself.
-- ============================================================================

CREATE TABLE legalcompliance_watch_sources (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    name                  VARCHAR(255) NOT NULL,
    authority             VARCHAR(255),
    feed_url              VARCHAR(500) NOT NULL,
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    last_fetched_at       TIMESTAMPTZ,
    last_status           VARCHAR(6) NOT NULL DEFAULT 'NEVER',
    last_error            VARCHAR(500),
    last_new_items        INTEGER NOT NULL DEFAULT 0,
    consecutive_failures  INTEGER NOT NULL DEFAULT 0,
    created_by            UUID,
    created_by_name       VARCHAR(255),
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_wsrc_url UNIQUE (tenant_id, feed_url),
    CONSTRAINT ck_lc_wsrc_status CHECK (last_status IN ('NEVER','OK','FAILED')),
    CONSTRAINT ck_lc_wsrc_url CHECK (feed_url LIKE 'https://%')
);
CREATE INDEX idx_lc_wsrc_tenant ON legalcompliance_watch_sources (tenant_id, active);

CREATE TABLE legalcompliance_watch_items (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    source_id        UUID NOT NULL REFERENCES legalcompliance_watch_sources (id),
    source_name      VARCHAR(255) NOT NULL,
    guid             VARCHAR(500) NOT NULL,
    title            VARCHAR(255) NOT NULL,
    link             VARCHAR(500),
    summary          VARCHAR(2000),
    published_on     DATE,
    fetched_at       TIMESTAMPTZ NOT NULL,
    status           VARCHAR(12) NOT NULL DEFAULT 'NEW',
    decision_note    TEXT,
    decided_by       UUID,
    decided_by_name  VARCHAR(255),
    decided_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_witem_guid UNIQUE (tenant_id, source_id, guid),
    CONSTRAINT ck_lc_witem_status CHECK (status IN ('NEW','RELEVANT','NOT_RELEVANT'))
);
CREATE INDEX idx_lc_witem_tenant ON legalcompliance_watch_items (tenant_id, status, published_on DESC);

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'LEGALCOMPLIANCE_MONITOR_MANAGE', 'Manage regulatory watch sources and triage what they publish')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name = 'LEGALCOMPLIANCE_MONITOR_MANAGE'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
