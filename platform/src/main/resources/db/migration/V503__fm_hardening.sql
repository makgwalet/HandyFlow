-- Chunk 2: Facilities Management hardening.
-- RENAME to the next free Flyway version, and apply AFTER the Chunk 1 migration
-- (which creates scheduler_locks).
--
-- PRE-CHECK (run manually BEFORE applying). The unique index below fails if a schedule
-- already has more than one open work order:
--
--   SELECT tenant_id, ppm_schedule_id, COUNT(*)
--   FROM fm_work_orders
--   WHERE ppm_schedule_id IS NOT NULL
--     AND status NOT IN ('COMPLETED','CANCELLED') AND deleted_at IS NULL
--   GROUP BY tenant_id, ppm_schedule_id HAVING COUNT(*) > 1;
--
-- Cancel the duplicates (keep the oldest) before migrating.

-- 1. Hold reason gets its own column (previously overwrote completion_notes).
ALTER TABLE fm_work_orders ADD COLUMN IF NOT EXISTS hold_reason VARCHAR(1000);

UPDATE fm_work_orders
   SET hold_reason = completion_notes,
       completion_notes = NULL
 WHERE status = 'ON_HOLD' AND hold_reason IS NULL AND completion_notes IS NOT NULL;

-- 2. At most one open work order per PPM schedule (idempotent PPM generation).
CREATE UNIQUE INDEX IF NOT EXISTS uq_fm_wo_open_ppm
    ON fm_work_orders (tenant_id, ppm_schedule_id)
    WHERE ppm_schedule_id IS NOT NULL
      AND status NOT IN ('COMPLETED', 'CANCELLED')
      AND deleted_at IS NULL;

-- 3. Billing: overlapping-period lookup used to block duplicate retainer invoices.
CREATE INDEX IF NOT EXISTS idx_fm_invoices_client_period
    ON fm_invoices (tenant_id, client_id, period_start, period_end);

-- 4. Portal: resolve a portal user's active grants quickly.
CREATE INDEX IF NOT EXISTS idx_fm_portal_grants_user_status
    ON fm_portal_access_grants (portal_user_id, status);