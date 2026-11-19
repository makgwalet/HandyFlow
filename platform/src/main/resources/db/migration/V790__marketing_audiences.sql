-- V252__marketing_audiences.sql  (rename to your next Flyway version)

CREATE TABLE IF NOT EXISTS mkt_segments (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    name            VARCHAR(150) NOT NULL,
    description     TEXT,
    segment_type    VARCHAR(10)  NOT NULL,          -- DYNAMIC | STATIC
    definition      JSONB,                          -- rules for DYNAMIC; NULL for STATIC
    last_count      INTEGER,                        -- last computed ELIGIBLE count (opted in, not suppressed)
    last_counted_at TIMESTAMPTZ,
    created_by      UUID,
    updated_by      UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_mkt_segments_name
    ON mkt_segments (tenant_id, lower(name)) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_mkt_segments_tenant
    ON mkt_segments (tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS mkt_segment_members (
    segment_id    UUID        NOT NULL REFERENCES mkt_segments (id) ON DELETE CASCADE,
    preference_id UUID        NOT NULL,
    tenant_id     UUID        NOT NULL,
    added_by      UUID,
    added_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (segment_id, preference_id)
);
CREATE INDEX IF NOT EXISTS idx_mkt_segment_members_pref ON mkt_segment_members (preference_id);

-- Campaigns point at a segment; the name is snapshotted so later renames don't rewrite history.
ALTER TABLE mkt_campaigns
    ADD COLUMN IF NOT EXISTS segment_id   UUID,
    ADD COLUMN IF NOT EXISTS segment_name VARCHAR(150);
CREATE INDEX IF NOT EXISTS idx_mkt_campaigns_segment ON mkt_campaigns (segment_id) WHERE segment_id IS NOT NULL;

-- Query-support indexes for server-side contact search and engagement filters.
CREATE INDEX IF NOT EXISTS idx_mkt_contact_prefs_tenant_created
    ON mkt_contact_preferences (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_mkt_campaign_contacts_pref_engagement
    ON mkt_campaign_contacts (preference_id, sent_at, opened_at, clicked_at);