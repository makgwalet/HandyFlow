-- HR slice 7b: overtime claims.
--
-- A claim is worked out from the attendance record (one per employee per day), approved or rejected by someone other than the
-- employee, and only an approved claim reaches payroll. The hourly rate, multiplier and amount are fixed when the claim is made.
-- Once a pay run has taken a claim, pay_run_id is set and the claim can no longer change.
--
-- HR_OVERTIME_APPROVE: approve or reject overtime. Given to roles that already hold HR_MANAGE.

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'HR_OVERTIME_APPROVE', 'Approve or reject overtime claims')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, p.id
FROM role_permissions rp
JOIN permissions held ON held.id = rp.permission_id AND held.name = 'HR_MANAGE'
CROSS JOIN permissions p
WHERE p.name = 'HR_OVERTIME_APPROVE'
ON CONFLICT DO NOTHING;

CREATE TABLE hr_overtime_claims (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID           NOT NULL REFERENCES tenants(id),
    employee_id    UUID           NOT NULL REFERENCES hr_employees(id),
    work_date      DATE           NOT NULL,
    kind           VARCHAR(10)    NOT NULL CHECK (kind IN ('OVERTIME', 'SUNDAY')),
    minutes        INT            NOT NULL CHECK (minutes > 0),
    multiplier     NUMERIC(4,2)   NOT NULL,
    hourly_rate    NUMERIC(12,4)  NOT NULL,
    amount         NUMERIC(12,2)  NOT NULL CHECK (amount >= 0),
    status         VARCHAR(10)    NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    requested_by   UUID,
    decided_by     UUID,
    decided_at     TIMESTAMPTZ,
    decision_note  VARCHAR(500),
    pay_run_id     UUID REFERENCES hr_pay_runs(id),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    version        BIGINT         NOT NULL DEFAULT 0,
    UNIQUE (employee_id, work_date)
);
CREATE INDEX hr_overtime_claims_tenant_status ON hr_overtime_claims (tenant_id, status);
CREATE INDEX hr_overtime_claims_pay_run ON hr_overtime_claims (pay_run_id);
