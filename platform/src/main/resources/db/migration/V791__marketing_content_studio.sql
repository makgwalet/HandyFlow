-- V253__marketing_content_studio.sql  (rename to your next Flyway version)

ALTER TABLE mkt_campaigns
    ADD COLUMN IF NOT EXISTS content_version  INTEGER     NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS review_status    VARCHAR(20) NOT NULL DEFAULT 'NOT_SUBMITTED',
    ADD COLUMN IF NOT EXISTS submitted_by     UUID,
    ADD COLUMN IF NOT EXISTS submitted_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reviewed_by      UUID,
    ADD COLUMN IF NOT EXISTS reviewed_at      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS review_note      TEXT,
    ADD COLUMN IF NOT EXISTS approved_version INTEGER,
    ADD COLUMN IF NOT EXISTS sent_version     INTEGER;

CREATE TABLE IF NOT EXISTS mkt_campaign_versions (
    campaign_id   UUID         NOT NULL,
    version       INTEGER      NOT NULL,
    tenant_id     UUID         NOT NULL,
    subject       TEXT         NOT NULL,
    html_body     TEXT         NOT NULL,
    from_name     VARCHAR(255),
    reply_to      VARCHAR(255),
    audience_type VARCHAR(20)  NOT NULL,
    segment_id    UUID,
    segment_name  VARCHAR(150),
    change_note   VARCHAR(300),
    created_by    UUID,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (campaign_id, version)
);
CREATE INDEX IF NOT EXISTS idx_mkt_campaign_versions_tenant ON mkt_campaign_versions (tenant_id, campaign_id);

CREATE TABLE IF NOT EXISTS mkt_content_blocks (
    id         UUID PRIMARY KEY,
    tenant_id  UUID         NOT NULL,
    name       VARCHAR(100) NOT NULL,
    category   VARCHAR(50),
    html       TEXT         NOT NULL,
    created_by UUID,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_mkt_blocks_name
    ON mkt_content_blocks (tenant_id, lower(name)) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS mkt_settings (
    tenant_id           UUID PRIMARY KEY,
    require_approval    BOOLEAN     NOT NULL DEFAULT false,
    allow_self_approval BOOLEAN     NOT NULL DEFAULT false,
    updated_by          UUID,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Backfill: existing campaigns become version 1; anything already launched records version 1 as sent.
INSERT INTO mkt_campaign_versions (campaign_id, version, tenant_id, subject, html_body, from_name, reply_to,
                                   audience_type, segment_id, segment_name, change_note, created_by, created_at)
SELECT id, 1, tenant_id, COALESCE(subject, ''), COALESCE(html_body, ''), from_name, reply_to,
       audience_type, segment_id, segment_name, 'Imported at upgrade', created_by, created_at
FROM mkt_campaigns
ON CONFLICT DO NOTHING;

UPDATE mkt_campaigns SET sent_version = 1
WHERE sent_version IS NULL AND status IN ('SENDING', 'SENT', 'PAUSED');