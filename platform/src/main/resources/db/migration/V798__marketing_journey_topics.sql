-- V260__marketing_journey_topics.sql  (rename to your next Flyway version)
ALTER TABLE mkt_journeys ADD COLUMN IF NOT EXISTS topic_id UUID;
CREATE INDEX IF NOT EXISTS idx_mkt_journeys_topic ON mkt_journeys (topic_id) WHERE topic_id IS NOT NULL;