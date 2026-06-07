-- V66__admin_tenant_invoices.sql
-- Phase 4: Admin invoicing to tenants.
-- Separate from the tenant-facing invoicing module (invoices table).
-- These are HandyFlow's own billing documents sent to tenant companies.

CREATE TABLE admin_tenant_invoices (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    invoice_number  VARCHAR(30) NOT NULL,           -- HF-INV-2026-0001
    tenant_id       UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    tenant_name     VARCHAR(255) NOT NULL,
    tenant_email    VARCHAR(255) NOT NULL,

    -- Billing period
    period_year     INT         NOT NULL,
    period_month    INT         NOT NULL,           -- 1-12
    period_label    VARCHAR(30) NOT NULL,           -- "June 2026"

    -- Amounts (ZAR)
    subtotal        NUMERIC(12,2) NOT NULL,
    vat_amount      NUMERIC(12,2) NOT NULL DEFAULT 0,
    total           NUMERIC(12,2) NOT NULL,
    vat_rate        NUMERIC(5,4)  NOT NULL DEFAULT 0.15,

    -- Line items stored as JSONB for flexibility
    -- [{moduleKey, name, price, status, trialEndsAt}]
    line_items      JSONB       NOT NULL DEFAULT '[]',

    -- Lifecycle
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','SENT','PAID','VOID')),
    sent_at         TIMESTAMP,
    paid_at         TIMESTAMP,
    due_date        DATE,

    -- PDF stored as base64 or file path
    pdf_base64      TEXT,

    -- Admin who generated it
    generated_by    UUID        REFERENCES admin_users(id),
    generated_by_email VARCHAR(255),

    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_admin_tenant_invoices PRIMARY KEY (id),
    CONSTRAINT uq_admin_invoice_number UNIQUE (invoice_number)
);

CREATE INDEX idx_admin_invoices_tenant   ON admin_tenant_invoices(tenant_id);
CREATE INDEX idx_admin_invoices_period   ON admin_tenant_invoices(period_year, period_month);
CREATE INDEX idx_admin_invoices_status   ON admin_tenant_invoices(status);

-- Invoice number sequence
CREATE SEQUENCE IF NOT EXISTS admin_invoice_seq START 1;
