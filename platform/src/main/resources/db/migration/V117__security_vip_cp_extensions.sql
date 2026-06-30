-- =============================================================================
-- V117__security_vip_cp_extensions.sql
-- Phase 3 — VIP/Close Protection Extensions (Part 9.2 remainder)
--
-- Three tables, deliberately reusing existing patterns rather than inventing
-- new ones, per the original design's explicit guidance:
--
--   security_advance_surveys     — recon check at an itinerary stop.
--     Same "expected vs actual, with tolerance" logic as patrol rounds
--     (Phase 2), just applied to a single recon event per stop instead of
--     a repeating patrol.
--
--   security_protection_vehicles — convoy vehicles (principal/lead/follow car).
--     A vehicle-flavored case of the resource custody pattern (Phase 2) —
--     checkout/condition/witness already exists in security_resource_custody
--     with resource_type='VEHICLE'; this table is the vehicle ASSET registry
--     (like Armoury is the firearm asset registry), distinct from any one
--     checkout event.
--
--   security_duress_events       — NOT a new table. Duress is just another
--     source value (DURESS) on the EXISTING security_alarm_events table from
--     Phase 3 Control Room. No new table needed — see the comment below
--     where this migration confirms DURESS is already a valid CHECK value
--     rather than creating a redundant table.
-- =============================================================================

CREATE TABLE IF NOT EXISTS security_advance_surveys (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID        NOT NULL REFERENCES tenants(id),
    itinerary_stop_id       UUID        NOT NULL REFERENCES security_itinerary_stops(id),

    surveyed_by_guard_id    UUID        NOT NULL REFERENCES security_guards(id),
    surveyed_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    entry_exit_routes_notes TEXT,
    hazards_noted           TEXT,
    photo_urls              JSONB,      -- array of photo URLs

    all_clear               BOOLEAN     NOT NULL DEFAULT false,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- One survey per stop per surveying guard — a stop CAN be re-surveyed
    -- by a different guard (second opinion) but not duplicated by the same
    -- guard, which would usually indicate a client/UI retry bug rather than
    -- a genuine second recon pass.
    CONSTRAINT uq_survey_stop_guard UNIQUE (itinerary_stop_id, surveyed_by_guard_id)
);

CREATE INDEX IF NOT EXISTS idx_advance_surveys_stop
    ON security_advance_surveys(itinerary_stop_id);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS security_protection_vehicles (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID        NOT NULL REFERENCES tenants(id),

    vehicle_type            VARCHAR(20) NOT NULL
        CHECK (vehicle_type IN ('PRINCIPAL_CAR', 'LEAD_CAR', 'FOLLOW_CAR')),

    registration             VARCHAR(20) NOT NULL,
    make_model                VARCHAR(150),
    armored                    BOOLEAN     NOT NULL DEFAULT false,

    -- Denormalized convenience, same pattern as Armoury.assigned_guard_id —
    -- the source of truth for who drove when is security_detail_assignments
    -- (role=DRIVER, vehicle_id set) and security_resource_custody, this is
    -- just "who's driving it right now" without a join.
    assigned_driver_guard_id UUID        REFERENCES security_guards(id),

    status                   VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'
        CHECK (status IN ('AVAILABLE', 'IN_USE', 'IN_SERVICE', 'DECOMMISSIONED')),

    notes                     TEXT,

    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_vehicle_registration_tenant UNIQUE (tenant_id, registration)
);

CREATE INDEX IF NOT EXISTS idx_protection_vehicles_tenant_status
    ON security_protection_vehicles(tenant_id, status)
    WHERE status != 'DECOMMISSIONED';

-- Now that the vehicle asset registry exists, the reserved vehicle_id columns
-- from V115 can properly reference it.
ALTER TABLE security_detail_assignments
    ADD CONSTRAINT fk_detail_assignment_vehicle
        FOREIGN KEY (vehicle_id) REFERENCES security_protection_vehicles(id);

-- ─────────────────────────────────────────────────────────────────────────────
-- DURESS EVENTS — no new table.
--
-- Part 9.2 of the design specifies security_duress_events as "a specialized,
-- higher-priority sibling of security_alarm_events," explicitly reusing the
-- alarm-event/dispatch pipeline rather than building a parallel one. The
-- Phase 3 Control Room migration (V114) already includes 'DURESS' as a valid
-- AlarmEvent.source value and 'triggered_by_guard_id' for exactly this case.
--
-- This migration adds the one missing piece: linking a duress trigger to the
-- protection detail it occurred on, so the control room can show "duress on
-- VIP-7's detail" rather than just "duress, somewhere."
-- =============================================================================

ALTER TABLE security_alarm_events
    ADD COLUMN IF NOT EXISTS protection_detail_id UUID REFERENCES security_protection_details(id);

CREATE INDEX IF NOT EXISTS idx_alarm_events_protection_detail
    ON security_alarm_events(protection_detail_id) WHERE protection_detail_id IS NOT NULL;
