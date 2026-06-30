-- =============================================================================
-- V116__security_cctv_registry.sql
-- Phase 3 — CCTV Registry
--
-- One table: security_cameras — a camera registry per site.
--
-- WHY a separate table rather than columns on security_sites?
-- A site can have multiple cameras (entrance, parking, server room) — this
-- is inherently one-to-many, not a single config blob per site. The original
-- audit (Part 2.4) recommended exactly this: "Build a camera registry per
-- site (RTSP/ONVIF URL or vendor cloud-API credential stored per site)."
--
-- WHY no video infrastructure here?
-- Per the audit: "Don't build video infrastructure." This table stores
-- connection metadata only (provider, URL/credential reference) so the
-- platform can reference a camera and receive its motion/event webhooks —
-- actual video streaming/storage stays with the vendor (Hikvision, Dahua,
-- cloud CCTV provider). HandyFlow is the software layer that unifies
-- event ingestion, not a video platform.
--
-- WHY connection_config as JSONB rather than typed columns?
-- Different vendors need different fields — an RTSP URL needs host/port/path,
-- a cloud API needs a tenant ID + API key, ONVIF needs a discovery endpoint.
-- Forcing one shape would mean nullable columns for fields most vendors
-- don't use. JSONB lets the integration layer validate per-provider shape
-- without a schema migration every time a new vendor is added.
--
-- CREDENTIAL STORAGE WARNING:
-- connection_config may contain API keys or camera passwords. This migration
-- does NOT add encryption — same gap as Part 9.3's principal medical_notes.
-- Flagging explicitly: a raw DB query sees plaintext credentials until
-- application-layer encryption is added in a follow-up pass.
--
-- Motion/alarm events from cameras feed the EXISTING AlarmEvent pipeline
-- (Phase 3 Control Room) via source=CCTV_MOTION — no separate event table
-- needed here, the camera_id just needs somewhere to attach on AlarmEvent.
-- =============================================================================

CREATE TABLE IF NOT EXISTS security_cameras (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL REFERENCES tenants(id),
    site_id             UUID        NOT NULL REFERENCES security_sites(id),

    name                VARCHAR(150) NOT NULL,    -- e.g. "Main Entrance", "Parking Level 1"
    provider            VARCHAR(30)  NOT NULL DEFAULT 'NONE'
        CHECK (provider IN ('HIKVISION_CLOUD', 'DAHUA_CLOUD', 'ONVIF', 'RTSP_GENERIC', 'OTHER', 'NONE')),

    -- NOT YET ENCRYPTED — see header note. May contain credentials/API keys.
    connection_config   JSONB,

    -- Webhook secret used to verify motion-event payloads claiming to be
    -- from this camera, same HMAC pattern as security_sites.qr_secret.
    webhook_secret       VARCHAR(100),

    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'OFFLINE', 'DECOMMISSIONED')),

    last_event_at        TIMESTAMPTZ,   -- updated on every ingested motion event — liveness signal

    notes                 TEXT,

    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cameras_site
    ON security_cameras(tenant_id, site_id) WHERE status != 'DECOMMISSIONED';

-- Link alarm events back to the originating camera, when applicable.
-- Nullable — most alarm sources (panic button, alarm panel) have no camera.
ALTER TABLE security_alarm_events
    ADD COLUMN IF NOT EXISTS camera_id UUID REFERENCES security_cameras(id);

CREATE INDEX IF NOT EXISTS idx_alarm_events_camera
    ON security_alarm_events(camera_id) WHERE camera_id IS NOT NULL;
