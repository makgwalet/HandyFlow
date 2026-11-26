-- V958: pricing and profitability (phase 6).
-- A client gets a minimum fee per run and extra fixed charges; a fee note is linked to the pay run it bills so one run cannot be
-- billed twice; a fee note can be cancelled (with a reason, before any payment); the bureau sets a cost per payslip.
ALTER TABLE pay_clients ADD COLUMN minimum_fee_per_run NUMERIC(10,2) NOT NULL DEFAULT 0;
ALTER TABLE pay_bureau_profiles ADD COLUMN cost_per_payslip NUMERIC(10,2) NOT NULL DEFAULT 0;

CREATE TABLE pay_client_charges (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL,
    pay_client_id UUID NOT NULL REFERENCES pay_clients(id),
    description   VARCHAR(200) NOT NULL,
    amount        NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    billing       VARCHAR(10) NOT NULL CHECK (billing IN ('PER_RUN','MONTHLY','ANNUAL')),
    status        VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    version       BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_pay_client_charges_client ON pay_client_charges (pay_client_id);

ALTER TABLE pay_fee_notes
    ADD COLUMN pay_run_id     UUID REFERENCES pay_runs(id),
    ADD COLUMN cancelled_at   TIMESTAMPTZ,
    ADD COLUMN cancelled_by   UUID,
    ADD COLUMN cancel_reason  VARCHAR(500);
ALTER TABLE pay_fee_note_lines ADD COLUMN charge_id UUID REFERENCES pay_client_charges(id);

-- Link existing fee notes to their run through the run number in their processing line. Where one run was billed more than once,
-- only the earliest note is linked (the others stay unlinked, so the unique index below can be created).
UPDATE pay_fee_notes f SET pay_run_id = x.run_id
FROM (
    SELECT DISTINCT ON (r.id) r.id AS run_id, f2.id AS note_id
    FROM pay_fee_notes f2
    JOIN pay_fee_note_lines l ON l.fee_note_id = f2.id
    JOIN pay_runs r ON r.pay_client_id = f2.pay_client_id AND l.description LIKE 'Payroll processing — ' || r.pay_run_number || ' (%'
    ORDER BY r.id, f2.created_at
) x
WHERE f.id = x.note_id;

CREATE UNIQUE INDEX uq_pay_fee_notes_active_run ON pay_fee_notes (pay_run_id) WHERE pay_run_id IS NOT NULL AND status <> 'CANCELLED';
