-- A20 (part 1): follow-up audits. An engagement can be marked as following up an earlier engagement; each earlier finding then gets
-- an outcome recorded by the follow-up team.
CREATE TABLE audit_followup_links (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    engagement_id        UUID NOT NULL,
    prior_engagement_id  UUID NOT NULL,
    created_by           UUID NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_audit_followup_link UNIQUE (tenant_id, engagement_id),
    CONSTRAINT ck_audit_followup_distinct CHECK (engagement_id <> prior_engagement_id)
);

CREATE TABLE audit_followup_items (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    engagement_id      UUID NOT NULL,
    finding_id         UUID NOT NULL,
    outcome            VARCHAR(25),        -- IMPLEMENTED | PARTIALLY_IMPLEMENTED | NOT_IMPLEMENTED | NO_LONGER_APPLICABLE
    note               TEXT,
    assessed_by        UUID,
    assessed_by_name   VARCHAR(255),
    assessed_at        TIMESTAMPTZ,
    CONSTRAINT uq_audit_followup_item UNIQUE (engagement_id, finding_id),
    CONSTRAINT ck_audit_followup_outcome CHECK (outcome IS NULL OR outcome IN ('IMPLEMENTED','PARTIALLY_IMPLEMENTED','NOT_IMPLEMENTED','NO_LONGER_APPLICABLE'))
);
CREATE INDEX idx_audit_followup_items_engagement ON audit_followup_items (tenant_id, engagement_id);
