-- Billing layer. Third migration for this module.

CREATE TABLE reca_invoices (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL,
    client_id        UUID NOT NULL REFERENCES reca_agency_clients(id),
    placement_id     UUID NOT NULL UNIQUE REFERENCES reca_placements(id),
    invoice_number   VARCHAR(30) NOT NULL,
    description      VARCHAR(500) NOT NULL,
    invoice_date     DATE NOT NULL,
    due_date         DATE NOT NULL,
    subtotal         NUMERIC(15,2) NOT NULL,
    vat_amount       NUMERIC(15,2) NOT NULL,
    total            NUMERIC(15,2) NOT NULL,
    amount_paid      NUMERIC(15,2) NOT NULL DEFAULT 0,
    status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    sent_at          TIMESTAMPTZ,
    paid_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reca_invoices_client ON reca_invoices (client_id);

CREATE TABLE reca_payments (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID NOT NULL,
    invoice_id             UUID NOT NULL REFERENCES reca_invoices(id),
    amount                 NUMERIC(15,2) NOT NULL,
    paid_date              DATE NOT NULL,
    method                 VARCHAR(30),
    reference              VARCHAR(100),
    recorded_by_user_id    UUID,
    recorded_by_user_name  VARCHAR(255),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reca_payments_invoice ON reca_payments (invoice_id);