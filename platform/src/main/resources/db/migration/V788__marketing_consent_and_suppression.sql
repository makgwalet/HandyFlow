-- V250__marketing_consent_and_suppression.sql  (rename to your next Flyway version)

-- ── Suppression list ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS mkt_suppression_entries (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    email       VARCHAR(320) NOT NULL,
    channel     VARCHAR(20)  NOT NULL DEFAULT 'EMAIL',
    reason      VARCHAR(30)  NOT NULL,           -- UNSUBSCRIBED | COMPLAINT | HARD_BOUNCE | MANUAL
    source      VARCHAR(50),
    note        TEXT,
    created_by  UUID,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    lifted_at   TIMESTAMPTZ,
    lifted_by   UUID,
    lift_reason TEXT
);

-- Only one ACTIVE suppression per tenant/channel/address; lifted rows are kept as history.
CREATE UNIQUE INDEX IF NOT EXISTS uq_mkt_suppression_active
    ON mkt_suppression_entries (tenant_id, channel, email)
    WHERE lifted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_mkt_suppression_tenant
    ON mkt_suppression_entries (tenant_id, created_at DESC);

-- ── Consent evidence (append-only audit trail) ──────────────────────────────
CREATE TABLE IF NOT EXISTS mkt_consent_evidence (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    preference_id UUID,
    email         VARCHAR(320) NOT NULL,
    channel       VARCHAR(20)  NOT NULL DEFAULT 'EMAIL',
    purpose       VARCHAR(30)  NOT NULL DEFAULT 'MARKETING',
    event_type    VARCHAR(30)  NOT NULL,         -- OPT_IN | OPT_OUT | IMPORT_DECLARATION | SUPPRESSION_ADDED | SUPPRESSION_LIFTED
    source        VARCHAR(50),
    evidence_text TEXT,
    recorded_by   UUID,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_mkt_consent_evidence_email
    ON mkt_consent_evidence (tenant_id, email, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_mkt_consent_evidence_pref
    ON mkt_consent_evidence (preference_id);

-- ── Campaign: suppressed counter ─────────────────────────────────────────────
ALTER TABLE mkt_campaigns
    ADD COLUMN IF NOT EXISTS suppressed_count INTEGER NOT NULL DEFAULT 0;

-- ── Query-support indexes ────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_mkt_send_queue_pending
    ON mkt_send_queue (scheduled_at) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_mkt_send_queue_tenant_email
    ON mkt_send_queue (tenant_id, to_email, status);
CREATE INDEX IF NOT EXISTS idx_mkt_campaign_contacts_campaign_pref
    ON mkt_campaign_contacts (campaign_id, preference_id);

-- ── New status values (SUPPRESSED / CANCELLED): drop any CHECK on status ─────
DO $$
DECLARE r record;
BEGIN
    FOR r IN
        SELECT conrelid::regclass AS tbl, conname
        FROM pg_constraint
        WHERE contype = 'c'
          AND conrelid IN ('mkt_campaign_contacts'::regclass, 'mkt_send_queue'::regclass)
          AND pg_get_constraintdef(oid) ILIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.tbl, r.conname);
    END LOOP;
END $$;

-- ── Backfill: existing opt-outs become active suppressions ──────────────────
INSERT INTO mkt_suppression_entries
    (id, tenant_id, email, channel, reason, source, note, created_at)
SELECT gen_random_uuid(), p.tenant_id, p.email, 'EMAIL', 'UNSUBSCRIBED',
       'MIGRATION_BACKFILL', 'Backfilled from existing opt-out',
       COALESCE(p.email_opted_out_at, now())
FROM mkt_contact_preferences p
WHERE p.email_opted_in = false
  AND p.email_opted_out_at IS NOT NULL
ON CONFLICT DO NOTHING;