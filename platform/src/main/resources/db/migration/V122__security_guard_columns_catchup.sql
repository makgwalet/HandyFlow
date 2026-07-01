-- =============================================================================
-- V122__security_guard_columns_catchup.sql
-- Adds all columns declared in Guard.java that are missing from the DB.
--
-- Root cause: Guard.java was updated across Phases 1.5, auth, 2, 3, and 4
-- using different column names than what the migrations actually deployed:
--   Guard.java uses:  pin_changed_at   → V110 deployed: pin_set_at
--   Guard.java uses:  pinFailedAttempts → V110 deployed: pin_failure_count
--   Guard.java uses:  status_changed_by → V102 never added this
-- Also adds Phase 4 columns (hourly_rate_cents, primary_branch_id) not yet in DB.
--
-- All ADD COLUMN IF NOT EXISTS — safe to run even if some columns exist.
-- =============================================================================

-- ── Guard.java field: pinChangedAt (@Column name = "pin_changed_at") ──────────
-- V110 added pin_set_at instead. Add the correct name as an alias.
ALTER TABLE security_guards
    ADD COLUMN IF NOT EXISTS pin_changed_at         TIMESTAMPTZ;

-- ── Guard.java field: pinFailedAttempts (@Column name = "pin_failed_attempts") ─
-- V110 added pin_failure_count instead. Add the correct name.
ALTER TABLE security_guards
    ADD COLUMN IF NOT EXISTS pin_failed_attempts     INTEGER NOT NULL DEFAULT 0;

-- ── Guard.java field: statusChangedBy (@Column name = "status_changed_by") ────
-- V102 added status_changed_at but not status_changed_by.
ALTER TABLE security_guards
    ADD COLUMN IF NOT EXISTS status_changed_by      UUID;

-- ── Phase 4: Payroll rate columns ─────────────────────────────────────────────
-- V121 adds these to the schema spec but the ALTER TABLE is in the migration;
-- adding here with IF NOT EXISTS as a safety net.
ALTER TABLE security_guards
    ADD COLUMN IF NOT EXISTS hourly_rate_cents       INTEGER,
    ADD COLUMN IF NOT EXISTS rate_effective_from     DATE,
    ADD COLUMN IF NOT EXISTS primary_branch_id       UUID;

-- ── Sync existing data: copy pin_failure_count → pin_failed_attempts ──────────
-- Guards who already had failed attempts tracked in pin_failure_count should
-- have that data reflected in the new canonical column name.
UPDATE security_guards
SET pin_failed_attempts = pin_failure_count
WHERE pin_failure_count > 0
  AND pin_failed_attempts = 0;

-- ── Sync pin_set_at → pin_changed_at for existing data ────────────────────────
UPDATE security_guards
SET pin_changed_at = pin_set_at
WHERE pin_set_at IS NOT NULL
  AND pin_changed_at IS NULL;