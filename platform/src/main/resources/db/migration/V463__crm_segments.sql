-- V463: saved customer segments. A segment is a name and a list of conditions; who is in it is worked out when it is read.
CREATE TABLE crm_segments (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    name        VARCHAR(60)  NOT NULL,
    conditions  JSONB        NOT NULL,
    created_by  UUID,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_crm_segments_tenant ON crm_segments (tenant_id);
