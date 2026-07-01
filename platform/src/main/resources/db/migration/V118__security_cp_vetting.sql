-- =============================================================================
-- V118__security_cp_vetting.sql
-- Part 9.5 — Officer Vetting (guard must be cleared before CP assignment)
-- Part 9.6 — Principal Vetting (company must vet the client before taking engagement)
--
-- WHY officer vetting reuses security_guard_screening_records (Phase 2)?
-- Phase 2 built a general-purpose screening history for every guard
-- (polygraph, criminal check, drug test). Part 9.5 doesn't need a new
-- screening table — it needs a vetting TIER column on the guard record that
-- maps to the threat level of the principal they'd be assigned to:
--   LOW threat    → standard screening (already CLEARED from Phase 2 gate)
--   MEDIUM threat → plus polygraph + credit check
--   HIGH threat   → plus enhanced background + international checks
--   CRITICAL      → full intelligence-level vetting
--
-- The DetailAssignment gate reads guard.cp_vetting_tier against the
-- principal's threat_level. If the guard's tier is below what the principal
-- requires, assignment is blocked (hard block, same as firearm competency).
--
-- WHY principal vetting is a separate table from guard screening?
-- The subjects are different — one is about the guard, the other is about
-- the client/protected person. The checks are different too: sanctions
-- screening, PEP (politically exposed person) status, adverse media, source
-- of funds — none of which map to the ScreeningType enum in
-- security_guard_screening_records.
-- =============================================================================

-- ── Part 9.5: Officer vetting tier on security_guards ─────────────────────────

ALTER TABLE security_guards
    ADD COLUMN IF NOT EXISTS cp_vetting_tier VARCHAR(20)
        CHECK (cp_vetting_tier IS NULL OR
               cp_vetting_tier IN ('STANDARD', 'ENHANCED', 'HIGH', 'CRITICAL')),
    ADD COLUMN IF NOT EXISTS cp_vetting_cleared_at  DATE,
    ADD COLUMN IF NOT EXISTS cp_vetting_expires_at  DATE;

-- ── Part 9.6: Principal vetting ───────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS security_principal_vetting (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL REFERENCES tenants(id),
    principal_id        UUID        NOT NULL REFERENCES security_principals(id),

    vetting_type        VARCHAR(30) NOT NULL
        CHECK (vetting_type IN (
            'SANCTIONS_SCREENING', 'PEP_CHECK', 'ADVERSE_MEDIA',
            'SOURCE_OF_FUNDS', 'CRIMINAL_ASSOCIATES', 'OTHER')),

    result              VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (result IN ('CLEAR', 'HIT', 'PENDING', 'INCONCLUSIVE')),

    conducted_by        VARCHAR(200),
    conducted_at        DATE,
    next_review_at      DATE,

    -- Encrypted pointer to the underlying report — same pattern as
    -- GuardScreeningRecord.reportRef: reference only, never raw content.
    report_ref          TEXT,
    notes               TEXT,

    created_by          UUID        REFERENCES security_guards(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_principal_vetting_principal
    ON security_principal_vetting(principal_id, conducted_at DESC);

-- Rollup on Principal — same pattern as guard.screening_status.
ALTER TABLE security_principals
    ADD COLUMN IF NOT EXISTS vetting_status VARCHAR(20) NOT NULL DEFAULT 'UNVETTED'
        CHECK (vetting_status IN ('UNVETTED', 'PENDING', 'CLEARED', 'FLAGGED'));

-- ── Declined principals register (Part 9.6) ───────────────────────────────────
-- If the company decides NOT to take an engagement due to vetting findings,
-- the principal goes on this register — separate from deactivation
-- (deactivation is an operational status; this is a compliance decision).

CREATE TABLE IF NOT EXISTS security_declined_principals (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL REFERENCES tenants(id),
    principal_id        UUID        NOT NULL REFERENCES security_principals(id),
    declined_at         DATE        NOT NULL DEFAULT CURRENT_DATE,
    declined_by         UUID        REFERENCES security_guards(id),
    reason              TEXT        NOT NULL,

    -- Encrypted — the declination reason often contains sensitive vetting
    -- intelligence that's even more restricted than the principal record itself
    encrypted_detail    TEXT,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_declined_principal UNIQUE (tenant_id, principal_id)
);
