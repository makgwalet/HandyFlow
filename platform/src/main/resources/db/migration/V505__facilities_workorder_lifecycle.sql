-- Chunk 3: Facilities permissions, site scope and audit.
-- RENAME to the next free Flyway version; apply AFTER the Chunk 1 migration.
--
-- PRE-CHECK (run manually first): the unique index on technician links fails if two
-- active technicians in a tenant are linked to the same user:
--   SELECT tenant_id, linked_user_id, COUNT(*) FROM facility_technicians
--   WHERE linked_user_id IS NOT NULL AND deleted_at IS NULL
--   GROUP BY tenant_id, linked_user_id HAVING COUNT(*) > 1;

-- 1. Per-user site restriction. A user with NO rows is unrestricted (backward compatible);
--    a user with one or more rows can only see/act on those sites. FACILITIES_ADMIN is never restricted.
CREATE TABLE IF NOT EXISTS facility_site_access (
    id         UUID PRIMARY KEY,
    tenant_id  UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    site_id    UUID        NOT NULL,
    granted_by UUID,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_facility_site_access UNIQUE (tenant_id, user_id, site_id)
);
CREATE INDEX IF NOT EXISTS idx_facility_site_access_user ON facility_site_access (tenant_id, user_id);

-- 2. Append-only audit log for sensitive actions.
CREATE TABLE IF NOT EXISTS facility_audit_log (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    actor_user_id UUID,
    actor_name    VARCHAR(255),
    action        VARCHAR(60)  NOT NULL,
    entity_type   VARCHAR(60)  NOT NULL,
    entity_id     UUID,
    details       VARCHAR(2000),
    created_at    TIMESTAMPTZ  NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_facility_audit_tenant_time ON facility_audit_log (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_facility_audit_entity ON facility_audit_log (tenant_id, entity_type, entity_id);

-- Tamper protection: rows can be inserted but never modified.
-- (DELETE is deliberately NOT blocked so a retention policy / tenant purge remains possible.)
CREATE OR REPLACE FUNCTION facility_audit_log_no_update() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'facility_audit_log is append-only';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_facility_audit_log_no_update ON facility_audit_log;
CREATE TRIGGER trg_facility_audit_log_no_update
    BEFORE UPDATE ON facility_audit_log
    FOR EACH ROW EXECUTE FUNCTION facility_audit_log_no_update();

-- 3. A user can be linked to at most one active technician per tenant
--    (the technician link drives "my assigned jobs" scope).
CREATE UNIQUE INDEX IF NOT EXISTS uq_facility_technician_linked_user
    ON facility_technicians (tenant_id, linked_user_id)
    WHERE linked_user_id IS NOT NULL AND deleted_at IS NULL;

-- 4. Scope queries.
CREATE INDEX IF NOT EXISTS idx_facility_wo_tenant_technician ON facility_work_orders (tenant_id, technician_id);