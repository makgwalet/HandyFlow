-- Recruiter: approval before a job is published and before an offer letter is sent, and retention of applicant data (POPIA).
-- Both approvals are off until a company switches them on, so nothing that works today starts to be refused.

CREATE TABLE IF NOT EXISTS rec_settings (
    tenant_id              UUID PRIMARY KEY,
    require_job_approval   BOOLEAN     NOT NULL DEFAULT FALSE,
    require_offer_approval BOOLEAN     NOT NULL DEFAULT FALSE,
    retention_months       INTEGER     NOT NULL DEFAULT 12 CHECK (retention_months BETWEEN 3 AND 60),
    updated_by             UUID,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE rec_jobs
    ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) NOT NULL DEFAULT 'NOT_SUBMITTED',
    ADD COLUMN IF NOT EXISTS submitted_by    UUID,
    ADD COLUMN IF NOT EXISTS submitted_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS approved_by     UUID,
    ADD COLUMN IF NOT EXISTS approved_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS approval_note   VARCHAR(500);
ALTER TABLE rec_jobs DROP CONSTRAINT IF EXISTS rec_jobs_approval_status_check;
ALTER TABLE rec_jobs ADD CONSTRAINT rec_jobs_approval_status_check
    CHECK (approval_status IN ('NOT_SUBMITTED', 'SUBMITTED', 'APPROVED', 'REJECTED'));
-- Jobs that have already been published went live without this step. They count as approved.
UPDATE rec_jobs SET approval_status = 'APPROVED' WHERE status <> 'DRAFT' AND approval_status = 'NOT_SUBMITTED';

ALTER TABLE rec_applications
    ADD COLUMN IF NOT EXISTS offer_approval_status VARCHAR(20) NOT NULL DEFAULT 'NOT_SUBMITTED',
    ADD COLUMN IF NOT EXISTS offer_submitted_by    UUID,
    ADD COLUMN IF NOT EXISTS offer_submitted_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS offer_approved_by     UUID,
    ADD COLUMN IF NOT EXISTS offer_approved_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS offer_approval_note   VARCHAR(500);
ALTER TABLE rec_applications DROP CONSTRAINT IF EXISTS rec_applications_offer_approval_check;
ALTER TABLE rec_applications ADD CONSTRAINT rec_applications_offer_approval_check
    CHECK (offer_approval_status IN ('NOT_SUBMITTED', 'SUBMITTED', 'APPROVED', 'REJECTED'));
-- Offers already sent were not approved through this step; they are not shown as awaiting approval.
UPDATE rec_applications SET offer_approval_status = 'APPROVED' WHERE offer_letter_sent_at IS NOT NULL;

-- Applicants whose personal details have been removed. The row stays so that counts and history still add up.
ALTER TABLE rec_applicants ADD COLUMN IF NOT EXISTS anonymised_at TIMESTAMPTZ;

-- What was removed and by whom, with no personal details of the applicant.
CREATE TABLE IF NOT EXISTS rec_retention_log (
    id           UUID PRIMARY KEY,
    tenant_id    UUID        NOT NULL,
    applicant_id UUID        NOT NULL,
    removed_by   UUID,
    removed_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    applications INTEGER     NOT NULL DEFAULT 0,
    had_cv       BOOLEAN     NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS rec_retention_log_tenant ON rec_retention_log (tenant_id, removed_at DESC);
