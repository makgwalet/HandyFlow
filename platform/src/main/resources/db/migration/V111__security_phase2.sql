-- =============================================================================
-- V111__security_phase2.sql
-- Phase 2 — Device/Session model, Patrol Rounds, Guard Screening
--
-- 1. security_devices          — site-owned or personal guard devices
-- 2. security_device_sessions  — active guard session per device (identity anchor)
-- 3. security_resource_custody — radio/key/firearm/device checkout per session
-- 4. security_patrol_routes    — named checkpoint sequences with interval config
-- 5. security_patrol_route_checkpoints — ordered checkpoints in a route
-- 6. security_patrol_rounds    — materialised expected patrol windows per shift
-- 7. security_guard_screening_records — polygraph/criminal check/drug test history
-- 8. round_id FK on security_checkpoint_logs
-- =============================================================================

-- ── 1. Devices ────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS security_devices (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL REFERENCES tenants(id),
    site_id             UUID        REFERENCES security_sites(id),  -- null = roaming spare
    device_hardware_id  VARCHAR(200) NOT NULL,
    device_name         VARCHAR(100),            -- human-readable label e.g. "Guardhouse Tablet"
    device_type         VARCHAR(30) NOT NULL DEFAULT 'SHARED_SITE_DEVICE'
        CHECK (device_type IN ('SHARED_SITE_DEVICE', 'PERSONAL_GUARD_DEVICE')),
    kiosk_mode_enabled  BOOLEAN     NOT NULL DEFAULT true,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'LOST', 'DECOMMISSIONED')),
    last_seen_at        TIMESTAMPTZ,
    battery_pct         INTEGER,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, device_hardware_id)
);

CREATE INDEX IF NOT EXISTS idx_devices_tenant_site
    ON security_devices(tenant_id, site_id) WHERE status = 'ACTIVE';

-- ── 2. Device Sessions ────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS security_device_sessions (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID        NOT NULL REFERENCES tenants(id),
    device_id                   UUID        NOT NULL REFERENCES security_devices(id),
    guard_id                    UUID        NOT NULL REFERENCES security_guards(id),
    shift_id                    UUID        REFERENCES security_shifts(id),

    started_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at                    TIMESTAMPTZ,             -- null = session still open

    -- Verification evidence captured at session start
    start_pin_verified          BOOLEAN     NOT NULL DEFAULT false,
    start_face_match_confidence DECIMAL(5,4),            -- 0.0000–1.0000; null = not captured
    start_geofence_ok           BOOLEAN,                 -- null = not checked (site has no geofence)

    -- Verification evidence at clock-out
    end_pin_verified            BOOLEAN,
    end_face_match_confidence   DECIMAL(5,4),

    handover_notes              TEXT,
    forced_close_reason         VARCHAR(200),            -- set if supervisor force-closed
    forced_close_by             UUID        REFERENCES security_guards(id),

    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Only one open session per device at a time
    -- (NULLS NOT DISTINCT: two rows with the same device_id and ended_at=NULL conflict)
    CONSTRAINT uq_device_open_session
        UNIQUE NULLS NOT DISTINCT (device_id, ended_at)
);

-- Only one open session per guard at a time (prevents "ghost" concurrent sessions)
CREATE UNIQUE INDEX IF NOT EXISTS uq_guard_open_session
    ON security_device_sessions(guard_id)
    WHERE ended_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_device_sessions_guard
    ON security_device_sessions(guard_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_device_sessions_shift
    ON security_device_sessions(shift_id) WHERE ended_at IS NULL;

-- ── 3. Resource Custody ───────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS security_resource_custody (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL REFERENCES tenants(id),
    session_id          UUID        NOT NULL REFERENCES security_device_sessions(id),
    guard_id            UUID        NOT NULL REFERENCES security_guards(id),
    shift_id            UUID        REFERENCES security_shifts(id),

    resource_type       VARCHAR(20) NOT NULL
        CHECK (resource_type IN ('RADIO', 'KEY', 'FIREARM', 'VEHICLE', 'OTHER')),
    resource_ref        VARCHAR(100) NOT NULL,   -- e.g. "Radio R-014", "Master Key Set B"
    resource_id         UUID,                    -- FK to armoury table (Phase 3) if applicable

    checked_out_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    checked_in_at       TIMESTAMPTZ,

    -- Two-person witness for radios/firearms (nullable for low-risk items)
    witnessed_by        UUID        REFERENCES security_guards(id),
    checkout_notes      TEXT,
    checkin_notes       TEXT,
    condition_on_return VARCHAR(20)
        CHECK (condition_on_return IS NULL OR
               condition_on_return IN ('GOOD', 'DAMAGED', 'MISSING')),

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_resource_custody_session
    ON security_resource_custody(session_id);

CREATE INDEX IF NOT EXISTS idx_resource_custody_open
    ON security_resource_custody(guard_id) WHERE checked_in_at IS NULL;

-- ── 4. Patrol Routes ──────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS security_patrol_routes (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id),
    site_id         UUID        NOT NULL REFERENCES security_sites(id),
    name            VARCHAR(120) NOT NULL,

    -- How often the full route repeats per shift (e.g. every 120 minutes)
    interval_minutes INTEGER     NOT NULL DEFAULT 120,
    -- Tolerance window either side of the expected start (e.g. ±20 minutes)
    tolerance_minutes INTEGER    NOT NULL DEFAULT 20,

    active          BOOLEAN     NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS security_patrol_route_checkpoints (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    route_id        UUID        NOT NULL REFERENCES security_patrol_routes(id) ON DELETE CASCADE,
    checkpoint_id   UUID        NOT NULL REFERENCES security_checkpoints(id),
    sequence        INTEGER     NOT NULL,            -- order within the route
    -- Per-checkpoint expected dwell window (null = use route-level interval)
    expected_minutes_after_route_start INTEGER,
    UNIQUE (route_id, sequence)
);

CREATE INDEX IF NOT EXISTS idx_patrol_routes_site
    ON security_patrol_routes(tenant_id, site_id) WHERE active = true;

-- ── 5. Patrol Rounds ──────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS security_patrol_rounds (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL REFERENCES tenants(id),
    site_id             UUID        NOT NULL REFERENCES security_sites(id),
    shift_id            UUID        NOT NULL REFERENCES security_shifts(id),
    route_id            UUID        REFERENCES security_patrol_routes(id),
    round_number        INTEGER     NOT NULL,        -- 1, 2, 3... within this shift

    expected_start_at   TIMESTAMPTZ NOT NULL,        -- derived from route interval + shift start
    expected_end_at     TIMESTAMPTZ NOT NULL,         -- expected_start_at + route total time

    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,

    status              VARCHAR(20) NOT NULL DEFAULT 'EXPECTED'
        CHECK (status IN ('EXPECTED', 'IN_PROGRESS', 'COMPLETE', 'PARTIAL', 'MISSED', 'OFF_SCHEDULE')),

    -- WHY store these counts?  Allows the compliance report to show "6 of 8 checkpoints
    -- scanned in round 3" without re-querying all checkpoint logs each time.
    checkpoints_expected INTEGER    NOT NULL DEFAULT 0,
    checkpoints_scanned  INTEGER    NOT NULL DEFAULT 0,

    off_schedule_reason  VARCHAR(200),   -- e.g. "Started 47 min early (expected 120 min interval)"

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    UNIQUE (shift_id, round_number)
);

CREATE INDEX IF NOT EXISTS idx_patrol_rounds_shift
    ON security_patrol_rounds(shift_id, round_number);

CREATE INDEX IF NOT EXISTS idx_patrol_rounds_expected
    ON security_patrol_rounds(expected_start_at)
    WHERE status IN ('EXPECTED', 'IN_PROGRESS');

-- ── 6. Add round_id to checkpoint_logs ────────────────────────────────────────

ALTER TABLE security_checkpoint_logs
    ADD COLUMN IF NOT EXISTS round_id UUID REFERENCES security_patrol_rounds(id);

CREATE INDEX IF NOT EXISTS idx_checkpoint_logs_round
    ON security_checkpoint_logs(round_id) WHERE round_id IS NOT NULL;

-- ── 7. Guard Screening Records ────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS security_guard_screening_records (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id),
    guard_id        UUID        NOT NULL REFERENCES security_guards(id),

    screening_type  VARCHAR(30) NOT NULL
        CHECK (screening_type IN (
            'POLYGRAPH', 'CRIMINAL_RECORD_CHECK', 'REFERENCE_CHECK',
            'DRUG_TEST', 'PSYCHOMETRIC', 'CREDIT_CHECK', 'OTHER')),

    reason          VARCHAR(30) NOT NULL DEFAULT 'ONBOARDING'
        CHECK (reason IN (
            'ONBOARDING', 'PERIODIC', 'POST_INCIDENT', 'RANDOM', 'CLIENT_REQUESTED')),

    result          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (result IN ('PASS', 'FAIL', 'INCONCLUSIVE', 'PENDING')),

    conducted_by    VARCHAR(200),           -- external agency or person name
    conducted_at    DATE,                   -- null while pending
    next_due_at     DATE,                   -- drives the expiry-alert scheduler
    report_ref      VARCHAR(500),           -- encrypted pointer / reference number only

    notes           TEXT,

    created_by      UUID        REFERENCES security_guards(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Rollup column on security_guards — computed from latest screening records
-- Scheduler updates this when a new screening result arrives.
ALTER TABLE security_guards
    ADD COLUMN IF NOT EXISTS screening_status VARCHAR(20) NOT NULL DEFAULT 'UNSCREENED'
        CHECK (screening_status IN ('UNSCREENED', 'PENDING', 'CLEARED', 'FLAGGED'));

CREATE INDEX IF NOT EXISTS idx_screening_guard
    ON security_guard_screening_records(guard_id, conducted_at DESC);

CREATE INDEX IF NOT EXISTS idx_screening_due
    ON security_guard_screening_records(next_due_at)
    WHERE result != 'FAIL' AND next_due_at IS NOT NULL;