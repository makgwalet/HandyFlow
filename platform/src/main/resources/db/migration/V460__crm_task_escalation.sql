-- V460: remember that managers were told about a late task, so they hear about it once.
-- Cleared when the task is rescheduled or reopened.
ALTER TABLE customer_followups
    ADD COLUMN IF NOT EXISTS escalation_sent_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_customer_followups_escalation
    ON customer_followups (tenant_id, due_date)
    WHERE completed_at IS NULL AND escalation_sent_at IS NULL;
