-- Events Chunk 2: append-only audit trail.
-- RENAME to your next Flyway version, e.g. V213__events_audit_log.sql

CREATE TABLE IF NOT EXISTS event_audit_log (
    id           UUID         PRIMARY KEY,
    tenant_id    UUID         NOT NULL,
    event_id     UUID         NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    action       VARCHAR(50)  NOT NULL,   -- e.g. EVENT_UPDATED, GUEST_CANCELLED
    entity_type  VARCHAR(50)  NOT NULL,   -- EVENT | GUEST | TIER | VENDOR
    entity_id    UUID,
    summary      VARCHAR(500) NOT NULL,
    details      TEXT,                    -- newline-separated change list, e.g. "title: A -> B"
    actor_id     UUID,                    -- NULL for system/scheduler actions
    actor_name   VARCHAR(200) NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_event_audit_event
    ON event_audit_log (tenant_id, event_id, occurred_at DESC);

-- Audit rows must never be edited after the fact. Deletes are deliberately NOT
-- blocked, so a future retention job (POPIA) or tenant purge can still remove them.
CREATE OR REPLACE FUNCTION event_audit_log_block_update() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'event_audit_log rows are immutable';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_event_audit_log_no_update ON event_audit_log;
CREATE TRIGGER trg_event_audit_log_no_update
    BEFORE UPDATE ON event_audit_log
    FOR EACH ROW EXECUTE FUNCTION event_audit_log_block_update();