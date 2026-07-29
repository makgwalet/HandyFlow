-- Rename this file to the actual next Flyway version before running.

CREATE TABLE customer_followups (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    customer_id       UUID NOT NULL REFERENCES customers(id),
    due_date          DATE NOT NULL,
    note              TEXT NOT NULL,
    assigned_to       UUID,
    completed_at      TIMESTAMPTZ,
    completed_by      UUID,
    reminder_sent_at  TIMESTAMPTZ,
    created_by        UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_customer_followups_customer ON customer_followups (tenant_id, customer_id, due_date);
CREATE INDEX idx_customer_followups_reminder ON customer_followups (tenant_id, due_date) WHERE completed_at IS NULL AND reminder_sent_at IS NULL;