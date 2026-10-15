-- V409: A6. Append-only record of audit actions: sharing with auditors, evidence downloads and auditor views, finding and
-- workpaper changes, report sign-off and reopening, auditor invites. The trigger refuses any UPDATE or DELETE.
CREATE TABLE audit_events (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL,
    actor_type   VARCHAR(20)  NOT NULL CHECK (actor_type IN ('STAFF', 'AUDITOR', 'SYSTEM')),
    actor_id     UUID,
    actor_name   VARCHAR(255),
    action       VARCHAR(60)  NOT NULL,
    item_type    VARCHAR(40)  NOT NULL,
    item_id      UUID,
    before_json  JSONB,
    after_json   JSONB,
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_events_item ON audit_events (tenant_id, item_type, item_id, occurred_at DESC);
CREATE INDEX idx_audit_events_time ON audit_events (tenant_id, occurred_at DESC);

CREATE FUNCTION audit_events_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_events_no_change
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_append_only();
