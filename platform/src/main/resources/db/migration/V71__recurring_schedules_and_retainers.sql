-- V71__recurring_schedules_and_retainers.sql

-- ── Recurring schedules ────────────────────────────────────────────────────
CREATE TABLE recurring_schedules (
    id                   UUID          NOT NULL PRIMARY KEY,
    value                UUID          NOT NULL,
    customer_id          UUID,
    title                VARCHAR(255)  NOT NULL,
    notes                TEXT,
    frequency            VARCHAR(20)   NOT NULL,
    frequency_day        INT,
    custom_interval_days INT,
    start_date           TIMESTAMPTZ   NOT NULL,
    end_date             TIMESTAMPTZ,
    next_run_at          TIMESTAMPTZ   NOT NULL,
    last_run_at          TIMESTAMPTZ,
    status               VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    currency             CHAR(3)       NOT NULL DEFAULT 'ZAR',
    walkin_client_name   VARCHAR(255),
    walkin_client_email  VARCHAR(255),
    walkin_client_phone  VARCHAR(50),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recurring_schedules_tenant   ON recurring_schedules (value);
CREATE INDEX idx_recurring_schedules_next_run ON recurring_schedules (next_run_at) WHERE status = 'ACTIVE';

-- ── Recurring line items ───────────────────────────────────────────────────
CREATE TABLE recurring_line_items (
    id                UUID          NOT NULL PRIMARY KEY,
    schedule_id       UUID          NOT NULL REFERENCES recurring_schedules (id) ON DELETE CASCADE,
    value             UUID          NOT NULL,
    catalogue_item_id UUID,
    description       VARCHAR(500)  NOT NULL,
    unit              VARCHAR(50)   NOT NULL,
    quantity          NUMERIC(12,4) NOT NULL,
    unit_price        NUMERIC(12,2) NOT NULL,
    vat_rate          NUMERIC(5,2)  NOT NULL DEFAULT 15.00,
    sort_order        INT           NOT NULL DEFAULT 0
);

CREATE INDEX idx_recurring_line_items_schedule ON recurring_line_items (schedule_id);

-- ── Invoice extensions ─────────────────────────────────────────────────────
ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS invoice_type          VARCHAR(30)   NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN IF NOT EXISTS recurring_schedule_id UUID,
    ADD COLUMN IF NOT EXISTS committed_hours       NUMERIC(10,2),
    ADD COLUMN IF NOT EXISTS rate_per_hour         NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS hours_consumed        NUMERIC(10,2) NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_invoices_recurring_schedule
    ON invoices (recurring_schedule_id)
    WHERE recurring_schedule_id IS NOT NULL;