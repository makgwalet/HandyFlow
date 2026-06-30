-- =============================================================================
-- V115__security_vip_close_protection_core.sql
-- Phase 3 — VIP / Close Protection Module (Core)
--
-- Four tables, per Part 9.2 of the design:
--   security_principals          — the person being protected
--   security_protection_details  — an engagement (replaces "Site contract" for CP work)
--   security_detail_assignments  — role-based team roster (replaces plain Shift)
--   security_itinerary_stops     — sequence of locations/times for mobile protection
--
-- WHY this doesn't reuse Site/Checkpoint/Shift:
-- A principal moves between locations in one day (office, residence,
-- restaurant, airport) — there's no single site to geofence. CP work is
-- itinerary-based, not patrol-based, and a detail is staffed by a coordinated
-- team with distinct roles (team leader, driver, CPO, advance) rather than
-- interchangeable guard slots on a shift. See Part 9.1 for the full
-- assumption-by-assumption breakdown of why the existing model breaks here.
--
-- CONFIDENTIALITY (Part 9.3):
-- Principal data needs an access tier above everything else in this module.
-- This migration does NOT add encryption — that's an application-layer
-- concern (encrypt/decrypt in the service layer before persisting
-- medical_notes/known_threats). The columns are still plain TEXT here; the
-- VIP_DETAIL_ACCESS authority gates the API endpoints, not the raw DB column.
-- Flagging this explicitly: a raw DB query still sees plaintext until
-- encryption is added in a follow-up pass.
-- =============================================================================

CREATE TABLE IF NOT EXISTS security_principals (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL REFERENCES tenants(id),

    full_name           VARCHAR(200) NOT NULL,
    -- Codename used in all team comms, dashboards, push notifications instead
    -- of the real name — protects against a lock-screen glance exposing who
    -- is being protected. Real name only resolvable via this table by
    -- someone with VIP_DETAIL_ACCESS.
    alias_codename       VARCHAR(50)  NOT NULL,

    threat_level         VARCHAR(20)  NOT NULL DEFAULT 'LOW'
        CHECK (threat_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),

    -- NOT YET ENCRYPTED — see header note. Plain TEXT until application-layer
    -- encryption is added; access is gated at the API layer in the meantime.
    medical_notes        TEXT,
    known_threats         TEXT,

    emergency_contacts    JSONB,       -- array of {name, relationship, phone}
    photo_url             TEXT,        -- restricted ACL at the application layer

    active                BOOLEAN      NOT NULL DEFAULT true,

    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_principal_codename_tenant UNIQUE (tenant_id, alias_codename)
);

CREATE INDEX IF NOT EXISTS idx_principals_tenant_active
    ON security_principals(tenant_id) WHERE active = true;

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS security_protection_details (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL REFERENCES tenants(id),
    principal_id        UUID        NOT NULL REFERENCES security_principals(id),

    detail_type         VARCHAR(20) NOT NULL
        CHECK (detail_type IN ('STATIC', 'MOBILE', 'EVENT', 'TRAVEL')),

    start_at            TIMESTAMPTZ NOT NULL,
    end_at              TIMESTAMPTZ,

    status               VARCHAR(20) NOT NULL DEFAULT 'PLANNED'
        CHECK (status IN ('PLANNED', 'ACTIVE', 'COMPLETED', 'CANCELLED')),

    billing_rate          DECIMAL(10,2),
    client_reference       VARCHAR(200),

    notes                  TEXT,

    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_protection_details_principal
    ON security_protection_details(principal_id, start_at DESC);

CREATE INDEX IF NOT EXISTS idx_protection_details_tenant_active
    ON security_protection_details(tenant_id, status)
    WHERE status IN ('PLANNED', 'ACTIVE');

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS security_detail_assignments (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL REFERENCES tenants(id),
    detail_id           UUID        NOT NULL REFERENCES security_protection_details(id),
    guard_id            UUID        NOT NULL REFERENCES security_guards(id),

    role                VARCHAR(30) NOT NULL
        CHECK (role IN ('TEAM_LEADER', 'DRIVER', 'CPO', 'ADVANCE', 'COUNTER_SURVEILLANCE')),

    assignment_start    TIMESTAMPTZ NOT NULL,
    assignment_end      TIMESTAMPTZ,

    -- Reserved for Phase 3.5 vehicle resource linkage (security_protection_vehicles)
    vehicle_id          UUID,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- A guard can hold at most one open-ended (assignment_end IS NULL) role
    -- on a given detail at a time — prevents accidentally double-booking the
    -- same guard into two roles on the same engagement.
    CONSTRAINT uq_detail_guard_open
        UNIQUE NULLS NOT DISTINCT (detail_id, guard_id, assignment_end)
);

CREATE INDEX IF NOT EXISTS idx_detail_assignments_detail
    ON security_detail_assignments(detail_id);

CREATE INDEX IF NOT EXISTS idx_detail_assignments_guard_open
    ON security_detail_assignments(guard_id)
    WHERE assignment_end IS NULL;

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS security_itinerary_stops (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID        NOT NULL REFERENCES tenants(id),
    detail_id               UUID        NOT NULL REFERENCES security_protection_details(id),

    sequence                INTEGER     NOT NULL,
    location_name           VARCHAR(200) NOT NULL,
    address                  TEXT,
    latitude                 DECIMAL(10,7),
    longitude                DECIMAL(10,7),

    scheduled_arrival         TIMESTAMPTZ,
    scheduled_departure       TIMESTAMPTZ,
    actual_arrival            TIMESTAMPTZ,
    actual_departure          TIMESTAMPTZ,

    -- Reserved for Phase 3.5 (security_advance_surveys) — whether a recon
    -- check is required before the principal arrives at this stop.
    advance_survey_required   BOOLEAN     NOT NULL DEFAULT false,

    notes                     TEXT,

    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_itinerary_detail_sequence UNIQUE (detail_id, sequence)
);

CREATE INDEX IF NOT EXISTS idx_itinerary_stops_detail
    ON security_itinerary_stops(detail_id, sequence);
