-- V512: client self-service requests from the FM client portal.
-- A work order raised by a portal user carries that user's id, so the portal can show a client its OWN
-- submitted/declined requests (staff-raised requests stay internal until approved) and so intake can be throttled.
ALTER TABLE fm_work_orders ADD COLUMN IF NOT EXISTS requested_by_portal_user_id UUID;

CREATE INDEX IF NOT EXISTS idx_fm_wo_portal_requests
    ON fm_work_orders (tenant_id, client_id, created_at)
    WHERE requested_by_portal_user_id IS NOT NULL;
