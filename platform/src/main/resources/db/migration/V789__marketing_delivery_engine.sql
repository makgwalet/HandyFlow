-- V251__marketing_delivery_engine.sql  (rename to your next Flyway version)

-- ── Send queue: claim tracking + provider reference ─────────────────────────
ALTER TABLE mkt_send_queue
    ADD COLUMN IF NOT EXISTS locked_at           TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS provider_message_id VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_mkt_send_queue_stale_claims
    ON mkt_send_queue (locked_at) WHERE status = 'SENDING';

-- ── Campaign contacts: delivery state machine ───────────────────────────────
-- delivery_status: QUEUED < ACCEPTED < DELIVERED < BOUNCED/FAILED < COMPLAINED
-- (separate from `status`, which tracks our own queue lifecycle)
ALTER TABLE mkt_campaign_contacts
    ADD COLUMN IF NOT EXISTS delivery_status     VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    ADD COLUMN IF NOT EXISTS provider_message_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS delivered_at        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS bounced_at          TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS complained_at       TIMESTAMPTZ;

-- Anything already marked SENT was handed to the mail system.
UPDATE mkt_campaign_contacts
SET delivery_status = 'ACCEPTED'
WHERE status = 'SENT' AND delivery_status = 'QUEUED';

-- ── Campaigns: new counters + auto-pause reason ─────────────────────────────
ALTER TABLE mkt_campaigns
    ADD COLUMN IF NOT EXISTS complained_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS failed_count     INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS pause_reason     TEXT;

-- ── Delivery events: append-only, de-duplicated webhook log ─────────────────
CREATE TABLE IF NOT EXISTS mkt_delivery_events (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID,                       -- NULL when the event could not be matched to one of our sends
    campaign_id         UUID,
    campaign_contact_id UUID,
    provider            VARCHAR(30)  NOT NULL,
    provider_event_id   VARCHAR(255) NOT NULL,
    event_type          VARCHAR(20)  NOT NULL,      -- DELIVERED | HARD_BOUNCE | SOFT_BOUNCE | COMPLAINT | UNSUBSCRIBE
    email               VARCHAR(320),
    occurred_at         TIMESTAMPTZ  NOT NULL,
    detail              TEXT,
    outcome             VARCHAR(20)  NOT NULL,      -- APPLIED | UNMATCHED
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Idempotency: a provider retrying the same event can never be applied twice.
CREATE UNIQUE INDEX IF NOT EXISTS uq_mkt_delivery_events_provider_event
    ON mkt_delivery_events (provider, provider_event_id);

CREATE INDEX IF NOT EXISTS idx_mkt_delivery_events_campaign
    ON mkt_delivery_events (tenant_id, campaign_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_mkt_campaign_contacts_provider_msg
    ON mkt_campaign_contacts (provider_message_id);