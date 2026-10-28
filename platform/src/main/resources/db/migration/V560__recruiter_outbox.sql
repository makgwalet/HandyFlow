-- Emails to candidates are written here in the same transaction as the change that causes them, and sent afterwards, with retries.
-- Before this, an email was sent straight away and any failure was only a log line: the candidate was never told and nobody could see it.
CREATE TABLE IF NOT EXISTS rec_outbox (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    application_id  UUID,
    kind            VARCHAR(30) NOT NULL,
    to_email        VARCHAR(255) NOT NULL,
    subject         VARCHAR(300) NOT NULL,
    body_html       TEXT NOT NULL,
    attachment_name VARCHAR(200),
    attachment      BYTEA,
    status          VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','FAILED')),
    attempts        INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_error      VARCHAR(300),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at         TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_rec_outbox_due ON rec_outbox (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_rec_outbox_tenant_status ON rec_outbox (tenant_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_rec_outbox_application ON rec_outbox (application_id);
