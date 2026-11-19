-- V257__marketing_attribution.sql  (rename to your next Flyway version)

CREATE TABLE IF NOT EXISTS mkt_conversions (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID         NOT NULL,
    external_ref        VARCHAR(100) NOT NULL,
    kind                VARCHAR(10)  NOT NULL,            -- PAYMENT | REFUND
    original_ref        VARCHAR(100),                     -- refunds: the payment being refunded
    email               VARCHAR(320) NOT NULL,
    preference_id       UUID,
    amount_cents        BIGINT       NOT NULL,            -- positive for payments, negative for refunds
    currency            CHAR(3)      NOT NULL,
    paid_at             TIMESTAMPTZ  NOT NULL,
    description         VARCHAR(200),
    source              VARCHAR(12)  NOT NULL,            -- INTEGRATION | MANUAL
    campaign_id         UUID,                             -- null = not attributed
    campaign_contact_id UUID,
    attributed_click_at TIMESTAMPTZ,
    window_days         INTEGER,
    created_by          UUID,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_mkt_conv_ref ON mkt_conversions (tenant_id, external_ref);
CREATE INDEX IF NOT EXISTS idx_mkt_conv_paid     ON mkt_conversions (tenant_id, paid_at DESC);
CREATE INDEX IF NOT EXISTS idx_mkt_conv_campaign ON mkt_conversions (campaign_id) WHERE campaign_id IS NOT NULL;

-- Manually entered campaign cost (for ROI) and the attribution window.
ALTER TABLE mkt_campaigns ADD COLUMN IF NOT EXISTS cost_cents BIGINT;
ALTER TABLE mkt_settings  ADD COLUMN IF NOT EXISTS attribution_window_days INTEGER NOT NULL DEFAULT 7;

-- Speeds up the "most recent click for this contact" lookup.
CREATE INDEX IF NOT EXISTS idx_mkt_cc_pref_clicked ON mkt_campaign_contacts (preference_id, clicked_at) WHERE clicked_at IS NOT NULL;