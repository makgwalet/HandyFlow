-- Rename this file to match your actual next Flyway/Liquibase version number —
-- V996 is a placeholder, same caveat as the other pending migrations from
-- this session (V997/V998/V999). Apply after those if they haven't landed yet.

CREATE TABLE task_checklist_items (
    id           UUID PRIMARY KEY,
    task_id      UUID NOT NULL REFERENCES tasks(id),
    tenant_id    UUID NOT NULL,
    text         VARCHAR(500) NOT NULL,
    completed    BOOLEAN NOT NULL DEFAULT false,
    sort_order   INT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_task_checklist_items_task_id ON task_checklist_items(task_id);