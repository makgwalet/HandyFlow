-- Adds escalation tracking so the nightly overdue job can tell which
-- threshold (1/3/7/14/30 days overdue) was last actioned for an invoice,
-- instead of firing exactly once and never again.
ALTER TABLE invoices
    ADD COLUMN overdue_reminder_stage INTEGER,
    ADD COLUMN last_overdue_reminder_sent_at TIMESTAMP;