-- Who did what in Recruiter and when. Written for every change staff make, and for the reads and exports that expose personal data.
CREATE TABLE IF NOT EXISTS rec_audit_log (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor_id       UUID,
    actor_name     VARCHAR(200),
    action         VARCHAR(40) NOT NULL,
    entity_type    VARCHAR(20) NOT NULL,
    entity_id      UUID,
    application_id UUID,
    detail         VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS idx_rec_audit_tenant_time ON rec_audit_log (tenant_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_rec_audit_application ON rec_audit_log (application_id, occurred_at DESC) WHERE application_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_rec_audit_actor_action ON rec_audit_log (tenant_id, actor_id, action, occurred_at DESC);
