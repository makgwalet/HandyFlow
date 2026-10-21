-- V457: CRM task queue. Customer follow-ups become tasks that can be listed across all customers.
ALTER TABLE customer_followups
    ADD COLUMN IF NOT EXISTS priority VARCHAR(10) NOT NULL DEFAULT 'NORMAL';

ALTER TABLE customer_followups
    ADD CONSTRAINT chk_customer_followups_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH'));

-- The queue reads every open task of a tenant in due-date order.
CREATE INDEX IF NOT EXISTS idx_customer_followups_open_queue
    ON customer_followups (tenant_id, due_date, created_at) WHERE completed_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_customer_followups_done
    ON customer_followups (tenant_id, completed_at DESC) WHERE completed_at IS NOT NULL;
