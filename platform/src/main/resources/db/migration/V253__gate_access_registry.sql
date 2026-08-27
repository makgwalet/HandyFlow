-- FIX: Gate Access & Registry sub-module (Step 1 — data model).
--
-- NUMBERING NOTE: V252 (RFI Change Order link) was the last confirmed-
-- delivered migration this session, but I don't have direct confirmation
-- it was actually applied successfully — no build log was reported back
-- after that delivery. This is numbered V253 as the best current
-- estimate; check the real flyway_schema_history state before running,
-- per this feature's own ground rule #1.

CREATE TABLE security_access_points (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    site_id      UUID NOT NULL,
    name         TEXT NOT NULL,
    description  TEXT,
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_access_points_tenant_site ON security_access_points(tenant_id, site_id);

-- No photoUrl column — deliberately superseding the original plan's own
-- §4 sketch. See GateRegisterEntry.java's own class Javadoc for the full
-- reasoning: photos/documents attach via the shared evidence module
-- instead, queried separately by entity_id.
--
-- id_number is stored raw (masked at the response-mapping layer, same
-- convention as security_guards.id_number) — encryption-at-rest is a
-- real, deliberate open question flagged in this feature's own design
-- notes, not applied here by default.
--
-- No FK constraints to sites/guards deliberately, matching this
-- module's own established convention elsewhere (e.g. DetailAssignment,
-- GuardLocationPing both reference guard_id/site_id as plain UUID
-- columns, no FK) — and specifically leaves room for the retention/
-- archival policy the plan's own §9 flags as likely needed later
-- without a hard FK complicating that.
CREATE TABLE security_gate_register_entries (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    site_id                     UUID NOT NULL,
    access_point_id             UUID NOT NULL,

    entry_type                  VARCHAR(30) NOT NULL,

    person_name                 TEXT NOT NULL,
    id_number                   TEXT,
    phone                       TEXT,
    company                     TEXT,

    host_name                   TEXT,
    host_contact                TEXT,
    purpose                     TEXT,

    vehicle_registration        TEXT,
    vehicle_make_model          TEXT,
    driver_name                 TEXT,

    id_scan_confidence          VARCHAR(20),

    logged_in_by_guard_id       UUID NOT NULL,
    logged_in_at                TIMESTAMPTZ NOT NULL,
    logged_out_by_guard_id      UUID,
    logged_out_at               TIMESTAMPTZ,

    linked_pre_registration_id  UUID,

    status                      VARCHAR(20) NOT NULL DEFAULT 'ON_SITE',
    overstay_alert_sent_at      TIMESTAMPTZ,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_gate_entries_tenant_site ON security_gate_register_entries(tenant_id, site_id);
CREATE INDEX idx_gate_entries_access_point ON security_gate_register_entries(access_point_id);

-- Backs "who is currently on site at Site X" (the on-site list, the
-- client portal count, and the overstay scheduler's own query) —
-- partial index, same technique as DeviceSession's own
-- "one open session per device" partial unique index, though this one
-- isn't unique (many entries can legitimately be ON_SITE at once).
CREATE INDEX idx_gate_entries_on_site
    ON security_gate_register_entries(tenant_id, site_id)
    WHERE logged_out_at IS NULL;