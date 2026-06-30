-- =============================================================================
-- V114__security_control_room.sql
-- Phase 3 — Control Room: Alarm Event Ingestion & Armed Response Dispatch
--
-- Two tables:
--   security_alarm_events — raw events from alarm panels, panic buttons,
--                            CCTV motion, drones, or any future source
--   security_dispatches   — the response action taken on an alarm event,
--                            with SLA timestamps (dispatched/arrived/resolved)
--
-- WHY a separate event/dispatch split rather than one table?
-- An alarm event can arrive and sit untriaged for a moment before anyone is
-- dispatched — the event and the response are genuinely different things
-- with different timestamps and different actors. Splitting them also means
-- a single event could theoretically receive a re-dispatch (first responder
-- unavailable, second one assigned) without losing the original event record.
--
-- WHY generic 'source' rather than separate tables per alarm type?
-- Part 7 of the design explicitly calls for the panic-button-as-alarm-event
-- pattern (a guard's panic press becomes a security_alarm_events row with
-- source=PANIC_BUTTON) and Part 9's duress events are designed to feed this
-- same pipeline as source=DURESS. One table with a source discriminator lets
-- every future event-driven feature reuse the same triage/dispatch/SLA flow
-- instead of building a parallel pipeline each time.
-- =============================================================================

CREATE TABLE IF NOT EXISTS security_alarm_events (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id),
    site_id         UUID        REFERENCES security_sites(id),  -- nullable: some sources are guard/principal-scoped, not site-scoped

    source          VARCHAR(20) NOT NULL
        CHECK (source IN ('ALARM_PANEL', 'PANIC_BUTTON', 'CCTV_MOTION', 'DRONE', 'DURESS', 'MANUAL', 'OTHER')),

    -- Raw payload from the originating system (alarm panel webhook body, etc.)
    -- kept verbatim for debugging/dispute resolution — never parsed back out
    -- as the source of truth, only as a reference.
    raw_payload     JSONB,

    severity        VARCHAR(20) NOT NULL DEFAULT 'MEDIUM'
        CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),

    status          VARCHAR(20) NOT NULL DEFAULT 'NEW'
        CHECK (status IN ('NEW', 'TRIAGED', 'DISPATCHED', 'RESOLVED', 'FALSE_ALARM')),

    -- Optional links to who/where this event relates to — all nullable
    -- because not every source has every linkage (a CCTV motion event has
    -- no guard_id, a panic button press does).
    triggered_by_guard_id  UUID    REFERENCES security_guards(id),
    latitude        DECIMAL(10,7),
    longitude       DECIMAL(10,7),

    description     TEXT,

    triaged_by      UUID        REFERENCES security_guards(id),
    triaged_at      TIMESTAMPTZ,

    -- Linked incident if one was auto-created or manually linked on resolution
    linked_incident_id UUID,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_alarm_events_tenant_status
    ON security_alarm_events(tenant_id, status)
    WHERE status IN ('NEW', 'TRIAGED', 'DISPATCHED');

CREATE INDEX IF NOT EXISTS idx_alarm_events_site
    ON security_alarm_events(site_id, created_at DESC);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS security_dispatches (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL REFERENCES tenants(id),
    alarm_event_id      UUID        NOT NULL REFERENCES security_alarm_events(id),

    dispatched_unit_type VARCHAR(20) NOT NULL
        CHECK (dispatched_unit_type IN ('ARMED_RESPONSE', 'GUARD', 'POLICE', 'OTHER')),

    -- Who was sent — nullable since POLICE dispatch may not map to an
    -- internal guard record.
    dispatched_guard_id UUID        REFERENCES security_guards(id),
    dispatched_by       UUID        REFERENCES security_guards(id),  -- control room operator who made the call

    -- SLA tracking — the whole point of this table.
    -- dispatched_at is set when the dispatch row is created; arrived_at and
    -- resolved_at are set by separate calls as the response unfolds, so
    -- response-time and resolution-time SLAs can be measured independently.
    dispatched_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    arrived_at          TIMESTAMPTZ,
    resolved_at         TIMESTAMPTZ,

    outcome             VARCHAR(20)
        CHECK (outcome IS NULL OR outcome IN ('RESOLVED', 'ESCALATED', 'FALSE_ALARM', 'NO_ACTION_NEEDED')),
    resolution_notes    TEXT,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dispatches_event
    ON security_dispatches(alarm_event_id);

CREATE INDEX IF NOT EXISTS idx_dispatches_open
    ON security_dispatches(tenant_id, dispatched_at)
    WHERE resolved_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_dispatches_guard
    ON security_dispatches(dispatched_guard_id, dispatched_at DESC)
    WHERE dispatched_guard_id IS NOT NULL;
