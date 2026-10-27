-- Chunk 5: Facilities Management permissions, client/site scope and audit.
-- RENAME to the next free Flyway version; apply AFTER the Chunk 2 migration.
--
-- PRE-CHECK (run manually first): the unique index on technician links fails if two active
-- technicians in a tenant are linked to the same user (new column, so this only matters if you
-- backfill linked_user_id before applying).

-- 1. Per-user client restriction. A user with NO rows is unrestricted (backward compatible);
--    with one or more rows they can only see/act on those clients (and those clients' sites,
--    assets, work orders, agreements, invoices and portal access). FACILITIESMANAGEMENT_ADMIN is never restricted.
CREATE TABLE IF NOT EXISTS fm_client_access (
    id         UUID PRIMARY KEY,
    tenant_id  UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    client_id  UUID        NOT NULL,
    granted_by UUID,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_fm_client_access UNIQUE (tenant_id, user_id, client_id)
);
CREATE INDEX IF NOT EXISTS idx_fm_client_access_user ON fm_client_access (tenant_id, user_id);

-- 2. Append-only audit log for sensitive commercial and operational actions.
CREATE TABLE IF NOT EXISTS fm_audit_log (
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
CREATE INDEX IF NOT EXISTS idx_fm_audit_tenant_time ON fm_audit_log (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_fm_audit_entity ON fm_audit_log (tenant_id, entity_type, entity_id);

-- Tamper protection: rows can be inserted but never modified.
-- (DELETE is deliberately NOT blocked so a retention policy / tenant purge remains possible.)
CREATE OR REPLACE FUNCTION fm_audit_log_no_update() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'fm_audit_log is append-only';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_fm_audit_log_no_update ON fm_audit_log;
CREATE TRIGGER trg_fm_audit_log_no_update
    BEFORE UPDATE ON fm_audit_log
    FOR EACH ROW EXECUTE FUNCTION fm_audit_log_no_update();

-- 3. FM technicians can now be linked to a system user (drives "my assigned jobs" scope),
--    exactly as in the internal Facilities module. One user <-> at most one active technician per tenant.
ALTER TABLE fm_technicians ADD COLUMN IF NOT EXISTS linked_user_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS uq_fm_technician_linked_user
    ON fm_technicians (tenant_id, linked_user_id)
    WHERE linked_user_id IS NOT NULL AND deleted_at IS NULL;

-- 4. Scope queries.
CREATE INDEX IF NOT EXISTS idx_fm_wo_tenant_technician ON fm_work_orders (tenant_id, technician_id);
CREATE INDEX IF NOT EXISTS idx_fm_wo_tenant_client     ON fm_work_orders (tenant_id, client_id);
CREATE INDEX IF NOT EXISTS idx_fm_sites_tenant_client  ON fm_sites (tenant_id, client_id);