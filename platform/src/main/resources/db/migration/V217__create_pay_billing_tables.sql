-- Bureau billing. Also adds the per-employee billing rate to pay_clients
-- (a real column addition to an existing table from this module's first
-- migration — safe, since that table's schema is entirely owned by this
-- module and only this session's own code writes to it).

ALTER TABLE pay_clients ADD COLUMN per_employee_fee NUMERIC(10,2) NOT NULL DEFAULT 50.00;
-- Default R50/employee/month — a placeholder starting rate, not a
-- researched market rate. Update per real client agreements; this just
-- ensures the column is never null for existing rows.

CREATE TABLE pay_fee_notes (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL,
    pay_client_id    UUID NOT NULL REFERENCES pay_clients(id),
    invoice_number   VARCHAR(30) NOT NULL,
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
CREATE INDEX idx_pay_fee_notes_client ON pay_fee_notes (pay_client_id);

CREATE TABLE pay_fee_note_lines (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fee_note_id   UUID NOT NULL REFERENCES pay_fee_notes(id),
    description   VARCHAR(500) NOT NULL,
    quantity      NUMERIC(8,2) NOT NULL,
    unit_price    NUMERIC(10,2) NOT NULL,
    amount        NUMERIC(15,2) NOT NULL
);
CREATE INDEX idx_pay_fee_note_lines_note ON pay_fee_note_lines (fee_note_id);

CREATE TABLE pay_payments (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL,
    fee_note_id   UUID NOT NULL REFERENCES pay_fee_notes(id),
    amount        NUMERIC(15,2) NOT NULL,
    paid_date     DATE NOT NULL,
    method        VARCHAR(30),
    reference     VARCHAR(100),
    recorded_by_user_id   UUID,
    recorded_by_user_name VARCHAR(255),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_pay_payments_fee_note ON pay_payments (fee_note_id);