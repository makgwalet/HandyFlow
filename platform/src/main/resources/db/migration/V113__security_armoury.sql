-- =============================================================================
-- V113__security_armoury.sql
-- Phase 3 — Armoury / Firearms Register (Firearms Control Act compliance)
--
-- Two tables:
--   security_armoury       — one row per physical firearm the company owns
--   security_armoury_logs  — every issue/return event, two-person witnessed
--
-- WHY a register separate from generic resource_custody?
-- security_resource_custody (Phase 2) tracks WHO currently holds an item for
-- the duration of a shift — it's a session-scoped checkout log.  The armoury
-- register tracks the asset itself: serial number, SAPS license, expiry,
-- current assignment, and full custody history independent of any one shift.
-- A firearm exists and has a compliance lifecycle (license renewal, service,
-- decommission) whether or not it's currently checked out.  resource_custody
-- rows reference security_armoury.id via resource_id when a firearm is
-- checked out for a shift — the two tables compose, they don't duplicate.
--
-- WHY two-person witness is mandatory here (not optional like radios)?
-- The Firearms Control Act requires verifiable chain-of-custody for licensed
-- firearms.  An unwitnessed issue/return is a compliance gap that could
-- expose the company to regulatory action if a firearm is ever lost or
-- misused — this is the one resource type where the witness step cannot be
-- skipped, unlike the optional witness on security_resource_custody.
-- =============================================================================

CREATE TABLE IF NOT EXISTS security_armoury (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID        NOT NULL REFERENCES tenants(id),

    firearm_serial          VARCHAR(100) NOT NULL,
    firearm_type            VARCHAR(50)  NOT NULL,   -- e.g. "Handgun", "Shotgun", "Rifle"
    make_model               VARCHAR(150),

    saps_license_number     VARCHAR(100) NOT NULL,
    license_issued_at       DATE,
    license_expiry          DATE         NOT NULL,

    -- Current assignment — nullable, set/cleared by issue/return events.
    -- This is a denormalized convenience column kept in sync by the service
    -- layer; security_armoury_logs is the source of truth for history.
    assigned_guard_id       UUID         REFERENCES security_guards(id),

    status                  VARCHAR(20)  NOT NULL DEFAULT 'IN_ARMOURY'
        CHECK (status IN ('IN_ARMOURY', 'ISSUED', 'LOST', 'DECOMMISSIONED')),

    last_service_at         DATE,
    next_service_due_at     DATE,

    notes                   TEXT,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_armoury_serial_tenant UNIQUE (tenant_id, firearm_serial)
);

CREATE INDEX IF NOT EXISTS idx_armoury_tenant_status
    ON security_armoury(tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_armoury_license_expiry
    ON security_armoury(tenant_id, license_expiry)
    WHERE status != 'DECOMMISSIONED';

CREATE INDEX IF NOT EXISTS idx_armoury_assigned_guard
    ON security_armoury(assigned_guard_id) WHERE status = 'ISSUED';

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS security_armoury_logs (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL REFERENCES tenants(id),
    armoury_id          UUID        NOT NULL REFERENCES security_armoury(id),
    guard_id            UUID        NOT NULL REFERENCES security_guards(id),

    action              VARCHAR(10) NOT NULL
        CHECK (action IN ('ISSUE', 'RETURN')),

    -- Mandatory two-person witness — see header comment for why this is
    -- NOT NULL here (vs the nullable witnessed_by on resource_custody).
    witnessed_by_guard_id UUID      NOT NULL REFERENCES security_guards(id),

    -- Link back to the Phase 2 session/shift this issue/return happened
    -- under, for cross-referencing with patrol and shift records.
    session_id          UUID        REFERENCES security_device_sessions(id),
    shift_id            UUID        REFERENCES security_shifts(id),

    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    condition_notes     TEXT,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_armoury_logs_armoury
    ON security_armoury_logs(armoury_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_armoury_logs_guard
    ON security_armoury_logs(guard_id, occurred_at DESC);

-- ── Guard-level firearm competency tracking ───────────────────────────────────
-- Same expiry-alert pattern as PSiRA grading — a guard's competency
-- certificate must be valid before they can be issued a firearm.

ALTER TABLE security_guards
    ADD COLUMN IF NOT EXISTS firearm_competency_number  VARCHAR(100),
    ADD COLUMN IF NOT EXISTS firearm_competency_expiry  DATE;

CREATE INDEX IF NOT EXISTS idx_guards_firearm_competency_expiry
    ON security_guards(tenant_id, firearm_competency_expiry)
    WHERE firearm_competency_expiry IS NOT NULL;
