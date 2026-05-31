-- V39__mkt_campaigns_version.sql
-- WHY? MktCampaign entity has @Version for optimistic locking
-- but V38 migration missed the version column.

ALTER TABLE mkt_campaigns ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;