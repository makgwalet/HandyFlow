-- Training: an audit trail of who changed what, and the permission to read it.
CREATE TABLE IF NOT EXISTS training_audit_log (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor_id    UUID,
    actor_name  VARCHAR(200),
    action      VARCHAR(40) NOT NULL,
    entity_type VARCHAR(20) NOT NULL,
    entity_id   UUID,
    detail      VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS idx_training_audit_tenant_time ON training_audit_log (tenant_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_training_audit_entity ON training_audit_log (entity_id, occurred_at DESC) WHERE entity_id IS NOT NULL;

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'TRAINING_AUDIT', 'See the Training audit trail')
ON CONFLICT (name) DO NOTHING;

-- Nothing changes on day one: every role that could issue and revoke certificates (TRAINING_ADMIN) also gets to read the trail.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
FROM role_permissions rp
JOIN permissions src ON src.id = rp.permission_id AND src.name = 'TRAINING_ADMIN'
JOIN permissions np ON np.name = 'TRAINING_AUDIT'
WHERE NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id);
