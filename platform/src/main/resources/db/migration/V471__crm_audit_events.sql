-- V471: a tenant-wide record of who changed what in the CRM (rules, workflows, reports, opportunities, groups, conversions, merges).
CREATE TABLE crm_audit_events (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    entity_type VARCHAR(30)  NOT NULL,
    entity_id   UUID,
    entity_name VARCHAR(200),
    action      VARCHAR(30)  NOT NULL,
    detail      VARCHAR(500),
    actor_id    UUID,                                   -- null = the system (a scheduled job or automation)
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_crm_audit_tenant_time ON crm_audit_events (tenant_id, created_at DESC);
CREATE INDEX idx_crm_audit_tenant_type ON crm_audit_events (tenant_id, entity_type, created_at DESC);
