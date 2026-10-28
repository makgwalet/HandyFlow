-- Saved HR reports: share one with colleagues, and ask for the reminder to be emailed as well.
-- A share gives the colleague the saved view (the report and the period), never figures: they see the figures under their own rights,
-- and the reminder email, like the in-app one, carries no figures.
ALTER TABLE hr_saved_reports ADD COLUMN IF NOT EXISTS email_reminder BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS hr_saved_report_shares (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id  UUID      NOT NULL REFERENCES hr_saved_reports(id) ON DELETE CASCADE,
    tenant_id  UUID      NOT NULL REFERENCES tenants(id),
    user_id    UUID      NOT NULL,
    shared_by  UUID,
    shared_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (report_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_hr_saved_report_shares_user ON hr_saved_report_shares (tenant_id, user_id);
