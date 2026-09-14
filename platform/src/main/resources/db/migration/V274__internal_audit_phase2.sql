-- V274__internal_audit_phase2.sql
-- Internal Audit Phase 2, per the agreed phased build: planning-detail
-- fields on the engagement, the full materiality model, and workpapers.
-- Workpaper tables are a direct structural mirror of V58's
-- acc_workpaper_folders/acc_workpaper_files (same review-status state
-- machine, same versioning approach) -- scoped to engagement_id rather
-- than client_id + engagement_year, since Internal Audit's workpapers
-- belong to one specific engagement, not a recurring per-year client
-- relationship.

ALTER TABLE audit_engagements
    ADD COLUMN objectives      TEXT,
    ADD COLUMN scope           TEXT,
    ADD COLUMN audit_criteria  TEXT,
    ADD COLUMN overall_materiality      NUMERIC(14,2),
    ADD COLUMN performance_materiality  NUMERIC(14,2),
    ADD COLUMN clearly_trivial_threshold NUMERIC(14,2);

COMMENT ON COLUMN audit_engagements.overall_materiality IS
    'Set once during planning by the auditor -- the default and common case. See audit_engagement_specific_materiality for the optional per-account/GL-segment override.';

-- FIX (agreed design): optional, only created when the auditor decides
-- a specific account genuinely needs its own threshold (the product
-- owner's own Petty Cash example -- a relatively small amount there can
-- matter for control/fraud reasons even when it's far below overall
-- materiality). Deliberately NOT a required per-account configuration
-- step -- that would make the tool painful to use for the common case,
-- per the product owner's own explicit warning.
CREATE TABLE audit_engagement_specific_materiality (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    engagement_id         UUID NOT NULL REFERENCES audit_engagements (id),
    account_or_gl_segment VARCHAR(200) NOT NULL,
    threshold             NUMERIC(14,2) NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_specific_materiality_engagement ON audit_engagement_specific_materiality (tenant_id, engagement_id);

CREATE TABLE audit_workpaper_folders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    engagement_id   UUID NOT NULL REFERENCES audit_engagements (id) ON DELETE CASCADE,
    parent_id       UUID REFERENCES audit_workpaper_folders (id),
    name            VARCHAR(200) NOT NULL,
    folder_type     VARCHAR(20)
        CHECK (folder_type IN ('PLANNING','FIELDWORK','SAMPLING','FINDINGS','REPORTING','GENERAL') OR folder_type IS NULL),
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE audit_workpaper_files (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL,
    engagement_id         UUID NOT NULL REFERENCES audit_engagements (id) ON DELETE CASCADE,
    folder_id             UUID NOT NULL REFERENCES audit_workpaper_folders (id),
    file_name             VARCHAR(300) NOT NULL,
    storage_key           VARCHAR(500), -- reserved, unused -- no S3 in this environment, same as acc_workpaper_files
    mime_type             VARCHAR(100),
    file_size_bytes       BIGINT,
    file_content_base64   TEXT NOT NULL,
    review_status         VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (review_status IN ('DRAFT','PREPARED','REVIEWED','SIGNED_OFF')),
    prepared_by           UUID,
    prepared_at           TIMESTAMP,
    reviewed_by           UUID,
    reviewed_at           TIMESTAMP,
    signed_off_by         UUID,
    signed_off_at         TIMESTAMP,
    version_number        INTEGER NOT NULL DEFAULT 1,
    superseded_by         UUID,
    deleted_at            TIMESTAMP,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_workpaper_folders_engagement ON audit_workpaper_folders (tenant_id, engagement_id);
CREATE INDEX idx_audit_workpaper_files_folder ON audit_workpaper_files (tenant_id, folder_id) WHERE deleted_at IS NULL;
