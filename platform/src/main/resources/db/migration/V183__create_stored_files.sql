-- Rename this file to match your actual next Flyway/Liquibase version number —
-- V997 is a placeholder so it doesn't collide with your real migration sequence
-- (same caveat as V999__add_task_overdue_alert_sent_at.sql and
-- V998__create_task_attachments.sql). If V998 hasn't been applied yet, this
-- one just needs to come after it, since task_attachments.storage_key values
-- now point at rows in this table.

CREATE TABLE stored_files (
    storage_key  VARCHAR(500) PRIMARY KEY,
    content_type VARCHAR(255),
    content      BYTEA NOT NULL,
    size_bytes   BIGINT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);