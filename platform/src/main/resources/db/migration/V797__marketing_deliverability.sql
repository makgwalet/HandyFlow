-- V259__marketing_deliverability.sql  (rename to your next Flyway version)
ALTER TABLE mkt_settings
    ADD COLUMN IF NOT EXISTS sending_domain             VARCHAR(255),
    ADD COLUMN IF NOT EXISTS dkim_selectors             VARCHAR(400),   -- comma-separated
    ADD COLUMN IF NOT EXISTS deliverability_result      JSONB,
    ADD COLUMN IF NOT EXISTS deliverability_checked_at  TIMESTAMPTZ;