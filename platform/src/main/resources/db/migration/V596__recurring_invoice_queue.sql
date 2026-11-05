-- A recurring invoice that could not be issued because the business is at its hard invoice cap is DELAYED, not skipped.
-- The occurrence waits here with its original scheduled billing date and the service period it covers, and is issued
-- exactly once, on the day capacity is available. Rows are never deleted; (schedule, scheduled date) is unique, so the
-- same occurrence can never be queued, or issued, twice.
CREATE TABLE IF NOT EXISTS recurring_invoice_queue (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL,
    schedule_id     UUID         NOT NULL,
    scheduled_for   DATE         NOT NULL,
    period_start    DATE         NOT NULL,
    period_end      DATE         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'QUEUED',
    queued_at       TIMESTAMP    NOT NULL DEFAULT now(),
    attempts        INTEGER      NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP,
    blocked_reason  VARCHAR(200),
    escalated_at    TIMESTAMP,
    invoice_id      UUID,
    issued_at       TIMESTAMP,
    CONSTRAINT chk_recurring_queue_status CHECK (status IN ('QUEUED', 'ISSUED')),
    CONSTRAINT uq_recurring_queue_occurrence UNIQUE (schedule_id, scheduled_for)
);
CREATE INDEX IF NOT EXISTS idx_recurring_queue_open ON recurring_invoice_queue (tenant_id, scheduled_for) WHERE status = 'QUEUED';
