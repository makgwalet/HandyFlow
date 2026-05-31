-- V32__subscription_grace_period.sql
-- Adds grace period tracking to subscriptions.
-- WHY past_due_since? We need to know WHEN the tenant went past due
-- so we can calculate when the 7-day grace period expires.
-- WHY grace_period_days? Configurable per tenant — default 7,
-- but sales can extend for good customers.

ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS past_due_since   TIMESTAMP,
    ADD COLUMN IF NOT EXISTS grace_period_days INTEGER NOT NULL DEFAULT 7;

CREATE INDEX idx_subscriptions_past_due
    ON subscriptions(past_due_since)
    WHERE past_due_since IS NOT NULL;
