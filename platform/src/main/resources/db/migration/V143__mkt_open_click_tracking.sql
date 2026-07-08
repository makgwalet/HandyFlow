-- V___mkt_open_click_tracking.sql
-- (rename to the next available Flyway version number in your sequence)
--
-- Backs open/click tracking — AnalyticsTab.tsx and CampaignsTab.tsx were
-- already built expecting openCount/clickCount on every campaign response;
-- neither field existed anywhere on the backend, so both dashboards were
-- silently showing 0% for every campaign via their own `?? 0` fallbacks.

ALTER TABLE mkt_campaigns
    ADD COLUMN IF NOT EXISTS open_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS click_count INT NOT NULL DEFAULT 0;

ALTER TABLE mkt_campaign_contacts
    ADD COLUMN IF NOT EXISTS opened_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS clicked_at TIMESTAMP;
