-- V589: IAM Phase 0 (items 0.3 and 0.5).
-- 1) Append-only record of access changes (invite, accept, cancel, resend, role create/edit, role assignment,
--    deactivate, reactivate, refused escalation attempts that were detected after load). Same trigger approach as V409.
-- 2) Invitation tokens are stored as SHA-256 hashes, never in clear text. Pending invitations created before this
--    migration keep working: the hash is computed from the existing token (the application hashes the presented token
--    the same way). The clear-text column is then dropped.
CREATE TABLE access_audit_events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL,
    actor_id      UUID,
    actor_name    VARCHAR(255),
    action        VARCHAR(60)  NOT NULL,
    target_type   VARCHAR(40)  NOT NULL,
    target_id     UUID,
    target_label  VARCHAR(255),
    before_json   JSONB,
    after_json    JSONB,
    occurred_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_access_audit_tenant_time ON access_audit_events (tenant_id, occurred_at DESC);
CREATE INDEX idx_access_audit_target ON access_audit_events (tenant_id, target_type, target_id, occurred_at DESC);

CREATE FUNCTION access_audit_events_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'access_audit_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_access_audit_events_no_change
    BEFORE UPDATE OR DELETE ON access_audit_events
    FOR EACH ROW EXECUTE FUNCTION access_audit_events_append_only();

ALTER TABLE user_invitations ADD COLUMN token_hash VARCHAR(64);
UPDATE user_invitations SET token_hash = encode(sha256(convert_to(token, 'UTF8')), 'hex');
ALTER TABLE user_invitations ALTER COLUMN token_hash SET NOT NULL;
ALTER TABLE user_invitations DROP COLUMN token;
CREATE UNIQUE INDEX ux_user_invitations_token_hash ON user_invitations (token_hash);
