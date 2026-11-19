-- V254__marketing_analytics.sql  (rename to your next Flyway version)

CREATE TABLE IF NOT EXISTS mkt_link_clicks (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID         NOT NULL,
    campaign_id         UUID         NOT NULL,
    campaign_contact_id UUID         NOT NULL,
    url                 TEXT         NOT NULL,
    url_hash            VARCHAR(32)  NOT NULL,
    click_count         INTEGER      NOT NULL DEFAULT 1,
    first_clicked_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_clicked_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- One row per recipient per destination: keeps the table bounded and makes unique-clicker counts exact.
CREATE UNIQUE INDEX IF NOT EXISTS uq_mkt_link_clicks_contact_url
    ON mkt_link_clicks (campaign_contact_id, url_hash);
CREATE INDEX IF NOT EXISTS idx_mkt_link_clicks_campaign
    ON mkt_link_clicks (campaign_id, tenant_id);

-- Supports the per-campaign funnel, timeline and recipient drilldown queries.
CREATE INDEX IF NOT EXISTS idx_mkt_cc_campaign_sent
    ON mkt_campaign_contacts (campaign_id, sent_at);