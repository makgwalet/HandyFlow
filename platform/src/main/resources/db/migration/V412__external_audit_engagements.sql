-- A11. An external audit engagement: which firm, which period, which entities, and whether it is running. Auditor invites attach to
-- one. Access works only while the engagement is ACTIVE; completing or archiving it ends access.
CREATE TABLE external_audit_engagements (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID         NOT NULL,
    name              VARCHAR(200) NOT NULL,
    firm_name         VARCHAR(200) NOT NULL,
    period_from       DATE         NOT NULL,
    period_to         DATE         NOT NULL,
    entities_in_scope TEXT,
    scope_note        TEXT,
    status            VARCHAR(10)  NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED', 'ACTIVE', 'COMPLETED', 'ARCHIVED')),
    created_by        UUID         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at        TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    completed_by      UUID,
    archived_at       TIMESTAMPTZ,
    archived_by       UUID,
    CHECK (period_to >= period_from)
);
CREATE INDEX idx_ext_audit_eng_tenant ON external_audit_engagements (tenant_id, status);

-- Existing grants stay valid: no engagement means the old behaviour.
ALTER TABLE auditor_access_grants ADD COLUMN engagement_id UUID REFERENCES external_audit_engagements (id);
CREATE INDEX idx_auditor_grants_engagement ON auditor_access_grants (engagement_id);
