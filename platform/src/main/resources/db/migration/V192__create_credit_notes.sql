-- Rename this file to the actual next Flyway version before running.

CREATE TABLE credit_notes (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    invoice_id          UUID NOT NULL REFERENCES invoices(id),
    credit_note_number  VARCHAR(20) NOT NULL,
    reason              VARCHAR(255),
    description         TEXT,
    subtotal            NUMERIC(15,2) NOT NULL,
    vat_total           NUMERIC(15,2) NOT NULL,
    total               NUMERIC(15,2) NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'ZAR',
    issued_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_credit_notes_tenant_number ON credit_notes (tenant_id, credit_note_number);
CREATE INDEX idx_credit_notes_invoice ON credit_notes (invoice_id);