-- Half-finished employee registrations, kept per person on the server so they survive a new browser or device.
-- The payload is the registration form as the page holds it. A draft is not an employee: it never reaches payroll,
-- leave or any count. It is deleted when the employee is registered, and drafts untouched for 30 days are cleared.
CREATE TABLE hr_employee_drafts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenants(id),
    owner_id    UUID         NOT NULL,
    label       VARCHAR(200),
    step        INT          NOT NULL DEFAULT 0 CHECK (step BETWEEN 0 AND 5),
    payload     TEXT         NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_hr_employee_drafts_owner ON hr_employee_drafts (tenant_id, owner_id, updated_at DESC);
