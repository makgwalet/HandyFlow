-- Morning reminder jobs remember what they have sent, so a second run on the same day (a restart, or a second application
-- instance) does not tell people twice. One row per message per person per day; the key is a hash of what was said and to whom,
-- so it holds no names, emails or text. Rows older than two weeks are deleted every night.

CREATE TABLE IF NOT EXISTS hr_reminders_sent (
    tenant_id  UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    sent_on    DATE        NOT NULL,
    digest     VARCHAR(64) NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, sent_on, digest)
);
CREATE INDEX IF NOT EXISTS idx_hr_reminders_sent_day ON hr_reminders_sent (sent_on);
