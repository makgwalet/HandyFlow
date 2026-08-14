-- Billing layer: flat monthly retainer invoices. Third migration for
-- this module, plus the monthly_retainer_amount column addition to
-- booka_agency_clients (see BookAgencyClient-billing-field.txt).

ALTER TABLE booka_agency_clients ADD COLUMN monthly_retainer_amount NUMERIC(15,2);

CREATE TABLE booka_invoices (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL,
    client_id        UUID NOT NULL REFERENCES booka_agency_clients(id),
    invoice_number   VARCHAR(30) NOT NULL,
    description      VARCHAR(500) NOT NULL,
    period_start     DATE NOT NULL,
    period_end       DATE NOT NULL,
    invoice_date     DATE NOT NULL,
    due_date         DATE NOT NULL,
    subtotal         NUMERIC(15,2) NOT NULL,
    vat_amount       NUMERIC(15,2) NOT NULL,
    total            NUMERIC(15,2) NOT NULL,
    amount_paid      NUMERIC(15,2) NOT NULL DEFAULT 0,
    status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    sent_at          TIMESTAMPTZ,
    paid_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_booka_invoice_client_period UNIQUE (client_id, period_start)
    -- prevents billing the same client twice for the same month —
    -- the periodic-billing equivalent of RecAgencyInvoice's one-per-
    -- placement uniqueness constraint
);
CREATE INDEX idx_booka_invoices_client ON booka_invoices (client_id);

CREATE TABLE booka_payments (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID NOT NULL,
    invoice_id             UUID NOT NULL REFERENCES booka_invoices(id),
    amount                 NUMERIC(15,2) NOT NULL,
    paid_date              DATE NOT NULL,
    method                 VARCHAR(30),
    reference              VARCHAR(100),
    recorded_by_user_id    UUID,
    recorded_by_user_name  VARCHAR(255),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_booka_payments_invoice ON booka_payments (invoice_id);