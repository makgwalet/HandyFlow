-- V948: per-employee payslip email delivery record (audit 2026-11-26, D10).
-- One row per payslip. A re-send skips SENT rows and retries FAILED ones,
-- so a partial failure no longer re-mails everybody or hides the failures.
CREATE TABLE pay_payslip_deliveries (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL,
    pay_run_id       UUID NOT NULL REFERENCES pay_runs(id),
    payslip_id       UUID NOT NULL REFERENCES pay_payslips(id),
    pay_employee_id  UUID NOT NULL REFERENCES pay_employees(id),
    status           VARCHAR(16) NOT NULL CHECK (status IN ('SENT','FAILED','NO_EMAIL')),
    email_to         VARCHAR(255),
    attempts         INTEGER NOT NULL DEFAULT 0,
    last_error       VARCHAR(500),
    last_attempt_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at          TIMESTAMPTZ,
    version          BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_pay_payslip_delivery_payslip ON pay_payslip_deliveries (payslip_id);
CREATE INDEX idx_pay_payslip_delivery_run ON pay_payslip_deliveries (tenant_id, pay_run_id);
