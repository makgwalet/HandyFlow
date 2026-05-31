-- V24__expenses_module.sql
-- WHY? Staff expense claims with approval workflow.
-- Approved claims auto-post to accounting as journal entries.

CREATE TABLE expense_claims (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    claim_number    VARCHAR(20) NOT NULL,
    employee_id     UUID,                     -- soft link to hr_employees
    submitted_by    UUID REFERENCES users(id),
    employee_name   VARCHAR(200) NOT NULL,
    claim_date      DATE NOT NULL,
    category        VARCHAR(50) NOT NULL
        CHECK (category IN ('TRAVEL','MEALS','ACCOMMODATION','FUEL',
                            'EQUIPMENT','OFFICE_SUPPLIES','MARKETING',
                            'ENTERTAINMENT','TELEPHONE','OTHER')),
    description     TEXT NOT NULL,
    amount          NUMERIC(10,2) NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'ZAR',
    receipt_url     TEXT,                     -- base64 or storage key
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','APPROVED','REJECTED','REIMBURSED')),
    approved_by     UUID REFERENCES users(id),
    approved_at     TIMESTAMP,
    rejection_reason TEXT,
    reimbursed_at   TIMESTAMP,
    journal_entry_id UUID,                    -- link to acc_journal_entries
    notes           TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, claim_number)
);

CREATE INDEX idx_expense_claims_tenant   ON expense_claims(tenant_id, status);
CREATE INDEX idx_expense_claims_employee ON expense_claims(employee_id);
CREATE INDEX idx_expense_claims_date     ON expense_claims(tenant_id, claim_date);