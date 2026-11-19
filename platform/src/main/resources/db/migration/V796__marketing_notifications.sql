-- V258__marketing_notifications.sql  (rename to your next Flyway version)
ALTER TABLE mkt_settings ADD COLUMN IF NOT EXISTS approver_emails TEXT;   -- comma-separated addresses