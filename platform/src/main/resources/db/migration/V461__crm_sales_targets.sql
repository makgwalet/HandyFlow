-- V461: monthly sales target per salesperson. One row per (account, owner, month); setting it again replaces the amount.
CREATE TABLE crm_sales_targets (
    id          UUID PRIMARY KEY,
    tenant_id   UUID           NOT NULL,
    owner_id    UUID           NOT NULL,
    period      VARCHAR(7)     NOT NULL,               -- 'YYYY-MM'
    amount      NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    set_by      UUID,
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_crm_sales_targets UNIQUE (tenant_id, owner_id, period),
    CONSTRAINT chk_crm_sales_targets_period CHECK (period ~ '^[0-9]{4}-(0[1-9]|1[0-2])$')
);
