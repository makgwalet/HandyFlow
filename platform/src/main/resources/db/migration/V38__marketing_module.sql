-- V38__marketing_module.sql
-- Marketing module — email campaigns, POPIA-compliant opt-in/unsubscribe,
-- async send queue, campaign analytics.
-- Phase 1: email only. Phase 2: SMS (Clickatell). Phase 3: WhatsApp Business API.

-- ── POPIA contact preferences ─────────────────────────────────────────────────
-- WHY a separate table? Every contact must explicitly opt in per channel.
-- POPIA section 69 requires consent to be specific, informed and voluntary.
-- Opt-out is permanent — we never remove this record, just set opted_in = false.
CREATE TABLE mkt_contact_preferences (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    -- Links to either a customer or a contact (soft reference)
    entity_type     VARCHAR(20) NOT NULL CHECK (entity_type IN ('CUSTOMER','CONTACT','SUBSCRIBER')),
    entity_id       UUID,               -- null for standalone subscribers (not in CRM)
    email           VARCHAR(255) NOT NULL,
    name            VARCHAR(255),
    -- Per-channel opt-in
    email_opted_in  BOOLEAN     NOT NULL DEFAULT false,
    sms_opted_in    BOOLEAN     NOT NULL DEFAULT false,
    whatsapp_opted_in BOOLEAN   NOT NULL DEFAULT false,
    -- Audit trail (POPIA requires this)
    email_opted_in_at   TIMESTAMP,
    email_opted_out_at  TIMESTAMP,
    opt_in_source       VARCHAR(50),    -- IMPORT, FORM, MANUAL, API
    unsubscribe_token   VARCHAR(100) UNIQUE,  -- secure token for unsubscribe link
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_mkt_prefs PRIMARY KEY (id),
    CONSTRAINT uq_mkt_prefs_tenant_email UNIQUE (tenant_id, email)
);

CREATE INDEX idx_mkt_prefs_tenant ON mkt_contact_preferences(tenant_id);
CREATE INDEX idx_mkt_prefs_email  ON mkt_contact_preferences(tenant_id, email);
CREATE INDEX idx_mkt_prefs_token  ON mkt_contact_preferences(unsubscribe_token);
CREATE INDEX idx_mkt_prefs_opted  ON mkt_contact_preferences(tenant_id, email_opted_in);

-- ── Email templates ───────────────────────────────────────────────────────────
CREATE TABLE mkt_templates (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    subject     VARCHAR(500) NOT NULL,
    -- HTML body — supports {{first_name}}, {{company_name}}, {{unsubscribe_url}} tokens
    html_body   TEXT        NOT NULL,
    preview_text VARCHAR(200),          -- shown in email client preview pane
    category    VARCHAR(50),            -- NEWSLETTER, PROMOTION, TRANSACTIONAL, etc.
    active      BOOLEAN     NOT NULL DEFAULT true,
    created_by  UUID        REFERENCES users(id),
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_mkt_templates PRIMARY KEY (id)
);

CREATE INDEX idx_mkt_templates_tenant ON mkt_templates(tenant_id);

-- ── Campaigns ─────────────────────────────────────────────────────────────────
CREATE TABLE mkt_campaigns (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    channel         VARCHAR(20)  NOT NULL DEFAULT 'EMAIL'
        CHECK (channel IN ('EMAIL','SMS','WHATSAPP')),
    template_id     UUID        REFERENCES mkt_templates(id) ON DELETE SET NULL,
    subject         VARCHAR(500),           -- override template subject
    html_body       TEXT,                   -- override template body
    -- Audience
    audience_type   VARCHAR(20) NOT NULL DEFAULT 'ALL_OPTED_IN'
        CHECK (audience_type IN ('ALL_OPTED_IN','SEGMENT','MANUAL')),
    -- For SEGMENT: JSON filter e.g. {"tags":["vip"],"city":"Johannesburg"}
    audience_filter JSONB,
    -- Scheduling
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','SCHEDULED','SENDING','SENT','PAUSED','CANCELLED')),
    scheduled_at    TIMESTAMP,             -- null = send immediately on launch
    sent_at         TIMESTAMP,
    -- Analytics (denormalised counters — updated by scheduler)
    recipient_count INT         NOT NULL DEFAULT 0,
    sent_count      INT         NOT NULL DEFAULT 0,
    delivered_count INT         NOT NULL DEFAULT 0,
    bounced_count   INT         NOT NULL DEFAULT 0,
    unsubscribed_count INT      NOT NULL DEFAULT 0,
    -- POPIA footer — must appear on every campaign email
    from_name       VARCHAR(255),          -- e.g. "Zeta Earthmoving"
    reply_to        VARCHAR(255),
    created_by      UUID        REFERENCES users(id),
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP,

    CONSTRAINT pk_mkt_campaigns PRIMARY KEY (id)
);

CREATE INDEX idx_mkt_campaigns_tenant ON mkt_campaigns(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_mkt_campaigns_status ON mkt_campaigns(tenant_id, status);

-- ── Campaign contacts (audience snapshot) ─────────────────────────────────────
-- WHY snapshot? At send time we lock in the audience.
-- If a contact unsubscribes after send, it doesn't retroactively change the list.
CREATE TABLE mkt_campaign_contacts (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    campaign_id     UUID        NOT NULL REFERENCES mkt_campaigns(id) ON DELETE CASCADE,
    tenant_id       UUID        NOT NULL,
    email           VARCHAR(255) NOT NULL,
    name            VARCHAR(255),
    preference_id   UUID        REFERENCES mkt_contact_preferences(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','SENT','DELIVERED','BOUNCED','UNSUBSCRIBED','FAILED')),
    sent_at         TIMESTAMP,
    error_message   TEXT,

    CONSTRAINT pk_mkt_campaign_contacts PRIMARY KEY (id),
    CONSTRAINT uq_mkt_cc_campaign_email UNIQUE (campaign_id, email)
);

CREATE INDEX idx_mkt_cc_campaign ON mkt_campaign_contacts(campaign_id);
CREATE INDEX idx_mkt_cc_status   ON mkt_campaign_contacts(campaign_id, status);
CREATE INDEX idx_mkt_cc_pending  ON mkt_campaign_contacts(status) WHERE status = 'PENDING';

-- ── Async send queue ──────────────────────────────────────────────────────────
-- WHY DB queue? Reliable, restartable, auditable.
-- Scheduler picks PENDING records in batches of 50 every 2 minutes.
-- On success: status = SENT. On failure: status = FAILED, retry_count++.
-- After 3 failures: status = DEAD — no more retries.
CREATE TABLE mkt_send_queue (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    campaign_id     UUID        NOT NULL REFERENCES mkt_campaigns(id) ON DELETE CASCADE,
    campaign_contact_id UUID    NOT NULL REFERENCES mkt_campaign_contacts(id),
    tenant_id       UUID        NOT NULL,
    to_email        VARCHAR(255) NOT NULL,
    to_name         VARCHAR(255),
    subject         VARCHAR(500) NOT NULL,
    html_body       TEXT        NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','PROCESSING','SENT','FAILED','DEAD')),
    retry_count     INT         NOT NULL DEFAULT 0,
    error_message   TEXT,
    scheduled_at    TIMESTAMP   NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMP,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_mkt_send_queue PRIMARY KEY (id)
);

CREATE INDEX idx_mkt_queue_pending ON mkt_send_queue(status, scheduled_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_mkt_queue_campaign ON mkt_send_queue(campaign_id);

-- ── Module catalogue ──────────────────────────────────────────────────────────
INSERT INTO module_catalogue (key, name, description, monthly_price, icon, category, sort_order)
VALUES (
    'marketing',
    'Marketing',
    'Email campaigns, POPIA-compliant opt-in/unsubscribe, audience segments from CRM contacts, campaign analytics. SMS and WhatsApp coming soon.',
    299.00, 'megaphone', 'OPERATIONS', 92
) ON CONFLICT (key) DO NOTHING;

INSERT INTO tenant_modules (tenant_id, module_key, status, trial_ends_at)
SELECT t.id, 'marketing', 'TRIAL', NOW() + INTERVAL '60 days'
FROM tenants t WHERE t.slug = 'zeta-earthmoving'
ON CONFLICT (tenant_id, module_key) DO NOTHING;

-- ── Permissions ───────────────────────────────────────────────────────────────
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'MARKETING_READ',   'View campaigns and analytics'),
    (gen_random_uuid(), 'MARKETING_MANAGE', 'Create and send marketing campaigns'),
    (gen_random_uuid(), 'MARKETING_ADMIN',  'Manage templates and contact preferences')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('MARKETING_READ','MARKETING_MANAGE','MARKETING_ADMIN')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
