-- Compliance Services: billing events. One row is one scheduled bill from an agreement. A person raises it as a DRAFT
-- invoice in Invoicing (never issued from here). The invoice link is unique, so one invoice cannot serve two events.

CREATE TABLE IF NOT EXISTS compliance_billing_events (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    agreement_id   UUID NOT NULL REFERENCES compliance_agreements(id),
    client_id      UUID NOT NULL REFERENCES compliance_service_clients(id),
    due_date       DATE NOT NULL,
    amount         NUMERIC(14,2) NOT NULL,                  -- ex VAT, as the agreement said when the event was made
    description    VARCHAR(500) NOT NULL,
    status         VARCHAR(12) NOT NULL DEFAULT 'SCHEDULED', -- SCHEDULED, INVOICED, SKIPPED
    invoice_id     UUID,
    invoice_number VARCHAR(60),
    raised_by      UUID,
    raised_at      TIMESTAMPTZ,
    skip_reason    TEXT,
    release_note   TEXT,                                     -- why an invoice was let go, kept after the event is raised again
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_compliance_billing_event_date UNIQUE (agreement_id, due_date),
    CONSTRAINT ck_compliance_billing_event_status CHECK (status IN ('SCHEDULED', 'INVOICED', 'SKIPPED')),
    CONSTRAINT ck_compliance_billing_event_amount CHECK (amount > 0),
    CONSTRAINT ck_compliance_billing_event_invoiced CHECK ((status = 'INVOICED') = (invoice_id IS NOT NULL)),
    CONSTRAINT ck_compliance_billing_event_skipped CHECK (status <> 'SKIPPED' OR skip_reason IS NOT NULL)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_billing_event_invoice ON compliance_billing_events(tenant_id, invoice_id) WHERE invoice_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_compliance_billing_events_agreement ON compliance_billing_events(tenant_id, agreement_id);
CREATE INDEX IF NOT EXISTS idx_compliance_billing_events_due ON compliance_billing_events(tenant_id, status, due_date);
