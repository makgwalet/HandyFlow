-- HR reports: a person can keep a view they use (a report and a period such as "last month") and ask to be reminded
-- weekly or monthly. The reminder is a notification with no figures in it; the figures are always read live, under the
-- rights of whoever opens the report. This table holds settings only.

CREATE TABLE IF NOT EXISTS hr_saved_reports (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID         NOT NULL REFERENCES tenants(id),
    owner_id         UUID         NOT NULL,
    name             VARCHAR(80)  NOT NULL,
    kind             VARCHAR(12)  NOT NULL CHECK (kind IN ('WORKFORCE','TIME','PAYROLL','COMPLIANCE')),
    period           VARCHAR(16)  NOT NULL CHECK (period IN ('THIS_MONTH','LAST_MONTH','THIS_QUARTER','LAST_QUARTER','YEAR_TO_DATE','LAST_90_DAYS','LAST_12_MONTHS')),
    schedule         VARCHAR(8)   NOT NULL DEFAULT 'NONE' CHECK (schedule IN ('NONE','WEEKLY','MONTHLY')),
    run_day          INTEGER,
    last_notified_on DATE,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    CHECK ((schedule = 'NONE' AND run_day IS NULL)
        OR (schedule = 'WEEKLY' AND run_day BETWEEN 1 AND 7)
        OR (schedule = 'MONTHLY' AND run_day BETWEEN 1 AND 28))
);
CREATE INDEX IF NOT EXISTS idx_hr_saved_reports_owner ON hr_saved_reports (tenant_id, owner_id, created_at);
CREATE INDEX IF NOT EXISTS idx_hr_saved_reports_scheduled ON hr_saved_reports (schedule) WHERE schedule <> 'NONE';
