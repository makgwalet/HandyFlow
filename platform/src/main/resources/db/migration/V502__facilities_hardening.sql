-- Chunk 1: Facilities hardening.
-- RENAME this file to the next free Flyway version (e.g. V123__facilities_hardening.sql).
--
-- PRE-CHECK (run manually BEFORE applying). The unique index below fails if a schedule
-- already has more than one open work order:
--
--   SELECT tenant_id, ppm_schedule_id, COUNT(*)
--   FROM facility_work_orders
--   WHERE ppm_schedule_id IS NOT NULL
--     AND status NOT IN ('COMPLETED','CANCELLED') AND deleted_at IS NULL
--   GROUP BY tenant_id, ppm_schedule_id HAVING COUNT(*) > 1;
--
-- Cancel the duplicates (keep the oldest) before migrating.

-- 1. Hold reason gets its own column (previously overwrote completion_notes).
ALTER TABLE facility_work_orders ADD COLUMN IF NOT EXISTS hold_reason VARCHAR(1000);

UPDATE facility_work_orders
   SET hold_reason = completion_notes,
       completion_notes = NULL
 WHERE status = 'ON_HOLD' AND hold_reason IS NULL AND completion_notes IS NOT NULL;

-- 2. DB-level guarantee: at most one open work order per PPM schedule.
--    Makes PPM generation idempotent even if two app instances race.
CREATE UNIQUE INDEX IF NOT EXISTS uq_facility_wo_open_ppm
    ON facility_work_orders (tenant_id, ppm_schedule_id)
    WHERE ppm_schedule_id IS NOT NULL
      AND status NOT IN ('COMPLETED', 'CANCELLED')
      AND deleted_at IS NULL;

-- 3. Sweep query support.
CREATE INDEX IF NOT EXISTS idx_facility_cert_status_expiry
    ON facility_compliance_certificates (status, expiry_date);

-- 4. Lease table for multi-instance-safe schedulers (used by shared.SchedulerLease).
CREATE TABLE IF NOT EXISTS scheduler_locks (
    lock_name    VARCHAR(100) PRIMARY KEY,
    locked_until TIMESTAMPTZ  NOT NULL,
    locked_by    VARCHAR(100) NOT NULL
);