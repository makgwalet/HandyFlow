-- V__PLACEHOLDER_ap_recurring_bill_templates.sql
-- RENAME: replace __PLACEHOLDER__ with your real next Flyway version.
--
-- No CHECK constraints on category/frequency/day_of_month — deliberately.
-- This exact module already hit a real bug from an unverified status
-- CHECK constraint (the SECOND_APPROVAL length issue) earlier this
-- session; validation for this table stays in application code only.

CREATE TABLE ap_recurring_bill_templates (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    supplier_id             UUID,
    supplier_name           VARCHAR(255) NOT NULL,
    category                VARCHAR(50) NOT NULL DEFAULT 'OTHER',
    description             VARCHAR(500) NOT NULL,
    amount                  NUMERIC(15,2) NOT NULL,
    vat_amount              NUMERIC(15,2) NOT NULL DEFAULT 0,
    frequency               VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    day_of_month            INT NOT NULL,
    lead_days               INT NOT NULL DEFAULT 7,
    next_due_date           DATE NOT NULL,
    last_generated_bill_id  UUID,
    last_generated_at       TIMESTAMPTZ,
    active                  BOOLEAN NOT NULL DEFAULT true,
    notes                   TEXT,
    created_by              UUID,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    deleted_at               TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_ap_recurring_templates_tenant ON ap_recurring_bill_templates(tenant_id);
-- Partial index — the scheduler only ever queries active templates, and
-- paused/deleted ones would otherwise bloat an index nothing uses them for.
CREATE INDEX idx_ap_recurring_templates_next_due ON ap_recurring_bill_templates(next_due_date) WHERE active = true;