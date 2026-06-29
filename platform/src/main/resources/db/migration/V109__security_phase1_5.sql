-- =============================================================================
-- V109__security_phase1_5.sql
-- Phase 1.5 — Operational Completeness
--
-- Adds:
--   1. PIN lifecycle columns to security_guards
--   2. security_rotation_patterns + security_rotation_assignments
--   3. security_shift_swap_requests
-- =============================================================================

-- ── 1. Guard PIN lifecycle ────────────────────────────────────────────────────

ALTER TABLE security_guards
    ADD COLUMN IF NOT EXISTS pin_hash                VARCHAR(72),   -- bcrypt 60 chars, headroom for future
    ADD COLUMN IF NOT EXISTS pin_set_at              TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS pin_expires_at          TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS pin_must_change         BOOLEAN     NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS pin_failure_count       INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS pin_locked_until        TIMESTAMPTZ,   -- set after N consecutive failures
    ADD COLUMN IF NOT EXISTS pin_history             TEXT;          -- JSON array of last 5 bcrypt hashes
                                                                    -- checked on change to prevent reuse

-- ── 2. Rotation patterns ──────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS security_rotation_patterns (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id),
    site_id         UUID        NOT NULL REFERENCES security_sites(id),
    name            VARCHAR(120) NOT NULL,

    -- Pattern type drives how cycle_definition is interpreted
    pattern_type    VARCHAR(30) NOT NULL
        CHECK (pattern_type IN ('FIXED_DAYS_ON_OFF', 'ALTERNATING_DAY_NIGHT', 'WEEKLY_FIXED', 'CUSTOM')),

    -- WHY JSONB for cycle_definition and not normalised columns?
    -- Different pattern_types have genuinely different shapes:
    --   FIXED_DAYS_ON_OFF: {"onDays": 4, "offDays": 2, "shiftLengthHours": 12}
    --   ALTERNATING_DAY_NIGHT: {"cycleWeeks": 2, "dayShiftHours": [6,18], "nightShiftHours": [18,6]}
    --   WEEKLY_FIXED: {"monday": "DAY", "tuesday": "DAY", "wednesday": "OFF", ...}
    -- Forcing all of these into a fixed column set would require many nullable
    -- columns and a complex CHECK constraint.  JSONB keeps the model clean and
    -- lets the application validate the shape per pattern_type.
    cycle_definition JSONB      NOT NULL DEFAULT '{}',

    shift_length_hours  INTEGER NOT NULL DEFAULT 12,  -- materialised convenience for schedule generation
    active              BOOLEAN NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS security_rotation_assignments (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id),
    pattern_id      UUID        NOT NULL REFERENCES security_rotation_patterns(id),
    guard_id        UUID        NOT NULL REFERENCES security_guards(id),

    -- When the guard begins following this pattern.
    -- WHY not enforce a unique (guard_id) constraint?
    -- A guard can be transferred from one pattern to another — the new
    -- assignment starts_at is in the future; the old one ends.  Keeping both
    -- rows gives a complete history of the guard's rotation schedule.
    starts_at       DATE        NOT NULL,
    ends_at         DATE,                     -- NULL = ongoing

    position_in_cycle  INTEGER NOT NULL DEFAULT 0,
    -- WHY track position_in_cycle?
    -- When a guard is added to a pattern mid-cycle (e.g. after sick leave),
    -- the schedule generator needs to know which day of the rotation to start
    -- them on, not always day 1.  0 = start of cycle.

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_rotation_active_guard
        UNIQUE NULLS NOT DISTINCT (guard_id, ends_at)
        -- Only one open-ended (ends_at IS NULL) assignment per guard.
        -- A guard can't follow two patterns simultaneously.
);

CREATE INDEX IF NOT EXISTS idx_rotation_patterns_tenant
    ON security_rotation_patterns(tenant_id) WHERE active = true;

CREATE INDEX IF NOT EXISTS idx_rotation_assignments_guard
    ON security_rotation_assignments(guard_id, starts_at);

CREATE INDEX IF NOT EXISTS idx_rotation_assignments_pattern
    ON security_rotation_assignments(pattern_id) WHERE ends_at IS NULL;

-- ── 3. Shift swap requests ────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS security_shift_swap_requests (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL REFERENCES tenants(id),

    -- The shift being swapped
    original_shift_id   UUID        NOT NULL REFERENCES security_shifts(id),

    -- The guard who wants to give away the shift
    requesting_guard_id UUID        NOT NULL REFERENCES security_guards(id),

    -- The guard who would take the shift (NULL = open request, any available guard)
    proposed_guard_id   UUID        REFERENCES security_guards(id),

    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROPOSED_ACCEPTED', 'APPROVED', 'REJECTED', 'CANCELLED')),

    -- WHY two acceptance stages (PROPOSED_ACCEPTED then APPROVED)?
    -- The proposed guard first accepts the swap (confirming they're willing
    -- and available), THEN a supervisor approves it.  This prevents a guard
    -- being assigned to a shift they didn't agree to, and prevents supervisors
    -- approving swaps the replacement guard wasn't consulted on.
    proposed_accepted_at TIMESTAMPTZ,  -- when proposed_guard accepted their end

    requested_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    decided_by          UUID        REFERENCES security_guards(id),  -- supervisor who approved/rejected
    decided_at          TIMESTAMPTZ,
    reason              TEXT,       -- requesting guard's reason for the swap
    rejection_reason    TEXT,       -- supervisor's reason if rejected

    -- Pre-swap validation snapshot — stored so the supervisor can see what
    -- the system checked at approval time even if guard records change later
    validation_passed   BOOLEAN,
    validation_notes    TEXT,       -- e.g. "overlap check passed; PSiRA valid until 2027-03"

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_swap_requests_tenant_status
    ON security_shift_swap_requests(tenant_id, status)
    WHERE status IN ('PENDING', 'PROPOSED_ACCEPTED');

CREATE INDEX IF NOT EXISTS idx_swap_requests_shift
    ON security_shift_swap_requests(original_shift_id);

CREATE INDEX IF NOT EXISTS idx_swap_requests_requesting_guard
    ON security_shift_swap_requests(requesting_guard_id, status);

CREATE INDEX IF NOT EXISTS idx_swap_requests_proposed_guard
    ON security_shift_swap_requests(proposed_guard_id, status)
    WHERE proposed_guard_id IS NOT NULL;
