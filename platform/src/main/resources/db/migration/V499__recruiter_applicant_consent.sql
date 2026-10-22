-- Recruiter: record that the applicant agreed to the company keeping their application (POPIA).
-- Applications made before this change have no record; they stay NULL rather than being marked as agreed.
ALTER TABLE rec_applications
    ADD COLUMN IF NOT EXISTS consent_given_at      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS consent_statement_ver VARCHAR(20);
