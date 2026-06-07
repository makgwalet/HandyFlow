-- V68__admin_notifications.sql
-- Phase 8: Real-time admin notifications
-- Stores lifecycle events that stream to the admin portal via SSE.

CREATE TABLE IF NOT EXISTS admin_notifications (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    type         VARCHAR(50) NOT NULL,  -- TENANT_SIGNED_UP, PILOT_EXPIRING, INVOICE_PAID, etc.
    title        VARCHAR(200) NOT NULL,
    body         TEXT,
    tenant_id    UUID,                  -- related tenant (nullable for system events)
    tenant_name  VARCHAR(200),          -- denormalised for fast display
    tenant_slug  VARCHAR(100),
    metadata     JSONB,                 -- extra context (invoice amount, days left, etc.)
    read_by      UUID[],                -- array of admin_user ids who have read it
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_admin_notifications PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_admin_notifications_created ON admin_notifications(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_notifications_type    ON admin_notifications(type);
CREATE INDEX IF NOT EXISTS idx_admin_notifications_tenant  ON admin_notifications(tenant_id);
