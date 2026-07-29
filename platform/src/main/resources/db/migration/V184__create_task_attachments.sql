-- Rename this file to match your actual next Flyway/Liquibase version number —
-- V998 is a placeholder so it doesn't collide with your real migration sequence
-- (same caveat as V999__add_task_overdue_alert_sent_at.sql).

CREATE TABLE task_attachments (
    id               UUID PRIMARY KEY,
    task_id          UUID NOT NULL REFERENCES tasks(id),
    tenant_id        UUID NOT NULL,
    file_name        VARCHAR(255) NOT NULL,
    content_type     VARCHAR(255),
    size_bytes       BIGINT NOT NULL,
    storage_key      TEXT NOT NULL,
    uploaded_by      UUID,
    uploaded_by_name VARCHAR(255) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_task_attachments_task_id ON task_attachments(task_id);