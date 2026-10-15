-- V408: A4. Once an engagement report is submitted or approved, the engagement's findings, workpapers and
-- planning are locked. The Head of Internal Audit can reopen them for amendment, with a reason, which is
-- recorded here. Resubmitting the report locks them again. Earlier workpaper versions stay readable.
CREATE TABLE audit_report_amendments (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL,
    engagement_id  UUID         NOT NULL,
    reason         TEXT         NOT NULL,
    amended_by     UUID         NOT NULL,
    amended_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resubmitted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_audit_report_amendment_open ON audit_report_amendments (tenant_id, engagement_id) WHERE resubmitted_at IS NULL;
CREATE INDEX idx_audit_report_amendments_eng ON audit_report_amendments (tenant_id, engagement_id);
