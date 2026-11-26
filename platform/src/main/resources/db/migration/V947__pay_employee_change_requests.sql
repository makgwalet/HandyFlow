-- V947: maker-checker for payroll bureau salary and bank-detail changes
-- (audit 2026-11-26, D4). A change to pay or bank details is stored as a
-- request; a different user holding PAYROLLBUREAU_APPROVE applies or
-- rejects it. Previous and proposed values are kept as the history.
CREATE TABLE pay_employee_change_requests (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL,
    pay_client_id         UUID NOT NULL REFERENCES pay_clients(id),
    pay_employee_id       UUID NOT NULL REFERENCES pay_employees(id),
    status                VARCHAR(12) NOT NULL DEFAULT 'PENDING'
                          CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    prev_gross_salary     NUMERIC(15,2),
    prev_travel_allowance NUMERIC(15,2),
    prev_pension          NUMERIC(15,2),
    prev_medical_aid      NUMERIC(15,2),
    prev_bank_name        VARCHAR(200),
    prev_bank_account     VARCHAR(100),
    prev_bank_branch      VARCHAR(50),
    new_gross_salary      NUMERIC(15,2),
    new_travel_allowance  NUMERIC(15,2),
    new_pension           NUMERIC(15,2),
    new_medical_aid       NUMERIC(15,2),
    new_bank_name         VARCHAR(200),
    new_bank_account      VARCHAR(100),
    new_bank_branch       VARCHAR(50),
    requested_by          UUID,
    requested_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_by            UUID,
    decided_at            TIMESTAMPTZ,
    decision_note         VARCHAR(500),
    version               BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_pay_emp_change_client ON pay_employee_change_requests (tenant_id, pay_client_id, status);
-- at most one open request per employee
CREATE UNIQUE INDEX uq_pay_emp_change_one_pending ON pay_employee_change_requests (pay_employee_id) WHERE status = 'PENDING';

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'PAYROLLBUREAU_APPROVE', 'Approve or reject payroll bureau salary and bank-detail changes')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN' AND p.name = 'PAYROLLBUREAU_APPROVE'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
