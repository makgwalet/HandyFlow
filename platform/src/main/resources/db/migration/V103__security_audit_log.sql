-- =============================================================================
-- V103__security_audit_log.sql
-- Bug #22: unified audit event log for the security module
-- Bug #10: documents the V45-V48 schema drift context
-- =============================================================================

-- ── Schema drift note (bug #10) ───────────────────────────────────────────────
-- V45: added acknowledged_at, resolved_at, updated_at to security_incidents
-- V46: added latitude, longitude, shift_id to security_incidents (geo fields)
-- V47: added status, severity, title, description, guard_id, shift_id,
--      latitude, longitude, acknowledged_at, resolved_at, updated_at, created_at
--      (many of these were already added in V45/V46 — ADD IF NOT EXISTS handled it)
-- V48: added type DEFAULT 'GENERAL' to security_incidents;
--      added contract columns to security_sites
-- V102: added guard status workflow, checkpoint NFC/BLE fields, incident actor
--       tracking, shift min_scan_count, supporting indexes
--
-- Current canonical schema as of V103:
--   security_guards:          V10 base + V102 status/psira_expiry
--   security_sites:           V10 base + V48 contract columns
--   security_checkpoints:     V10 base + V102 nfc_tag_uid/ble_beacon_id
--   security_shifts:          V11 base + V102 min_scan_count
--   security_checkpoint_logs: V11 base + V102 scan_type
--   security_incidents:       V12 base + V45-V48 columns + V102 acknowledged_by/resolved_by
-- =============================================================================

-- ── Unified audit log (bug #22) ───────────────────────────────────────────────
-- WHY a single table for all audit events?
-- The scattered *_by columns (deleted_by, acknowledged_by, resolved_by) give
-- partial answers to "who did what when" but require different queries for each
-- entity type.  A central log gives one query for any entity's full history.
--
-- This table is append-only.  No UPDATE, no DELETE, ever.
-- Every significant action on a security entity produces one row here.
--
-- WHY jsonb for old_values/new_values?
-- Different entities have different fields.  A single column per field would
-- require altering this table every time a new entity is added.  JSONB is
-- flexible and supports indexed queries on specific fields when needed.
CREATE TABLE IF NOT EXISTS security_audit_log (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     UUID        NOT NULL,
    actor_id      UUID,               -- NULL for system-generated events (schedulers)
    actor_type    VARCHAR(20) NOT NULL DEFAULT 'USER',  -- USER | SYSTEM | GUARD_APP
    entity_type   VARCHAR(50) NOT NULL,  -- GUARD | SITE | SHIFT | INCIDENT | CHECKPOINT | SCAN
    entity_id     UUID        NOT NULL,
    action        VARCHAR(50) NOT NULL,  -- CREATED | UPDATED | DELETED | STATUS_CHANGED |
                                         -- ACKNOWLEDGED | RESOLVED | TERMINATED | SCANNED
    old_values    JSONB,               -- state before the action (null for CREATE)
    new_values    JSONB,               -- state after the action (null for DELETE)
    metadata      JSONB,               -- extra context: IP, device_id, shift_id, scan_type
    occurred_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_security_audit_log PRIMARY KEY (id)
);

-- Tenant + entity lookup (most common query: "all events for guard X")
CREATE INDEX IF NOT EXISTS idx_audit_entity
    ON security_audit_log(tenant_id, entity_type, entity_id, occurred_at DESC);

-- Actor lookup ("all actions taken by supervisor Y this week")
CREATE INDEX IF NOT EXISTS idx_audit_actor
    ON security_audit_log(tenant_id, actor_id, occurred_at DESC)
    WHERE actor_id IS NOT NULL;

-- Time-range query ("all events today")
CREATE INDEX IF NOT EXISTS idx_audit_time
    ON security_audit_log(tenant_id, occurred_at DESC);

COMMENT ON TABLE security_audit_log IS
    'Append-only audit trail for all significant security module events. '
    'Never UPDATE or DELETE rows. Query with idx_audit_entity for entity history, '
    'idx_audit_actor for supervisor actions, idx_audit_time for time-range reports.';
