-- HR slice 5: tax certificates (IRP5 / IT3(a)) and the reconciliation with the EMP201 returns.
--
-- 1. A permission to issue, cancel and release certificates (viewing and exporting use PAYROLL_STATUTORY_EXPORT).
-- 2. The medical tax credit actually applied against PAYE is kept on each payslip from now on (code 4116). Payslips made
--    before this migration have none recorded, and it cannot be rebuilt reliably, so they stay NULL and the certificate says so.
-- 3. The employer's tax details and the type of retirement fund, one row per tenant.
-- 4. Issued certificates and their code lines. A certificate cannot be changed or deleted once issued; it can be
--    cancelled, and a corrected one is issued as the next version with a new number (SARS does not allow a number to be reused).

-- ── 1. Permission ────────────────────────────────────────────────────────────────────────────────
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'PAYROLL_CERTIFICATE_ISSUE', 'Issue, cancel and release tax certificates (IRP5 / IT3(a))')
ON CONFLICT (name) DO NOTHING;

-- Same rule as approving and finalising: the tenant administration role holds it and nobody else is given it.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN' AND p.name = 'PAYROLL_CERTIFICATE_ISSUE'
ON CONFLICT DO NOTHING;

-- ── 2. Medical credit on payslips ────────────────────────────────────────────────────────────────
ALTER TABLE hr_payslips ADD COLUMN IF NOT EXISTS medical_tax_credit NUMERIC(12,2);

-- ── 3. Tax settings ──────────────────────────────────────────────────────────────────────────────
CREATE TABLE hr_tax_settings (
    tenant_id            UUID PRIMARY KEY REFERENCES tenants(id),
    paye_reference       VARCHAR(20),
    sdl_reference        VARCHAR(20),
    uif_reference        VARCHAR(20),
    retirement_fund_type VARCHAR(30) NOT NULL DEFAULT 'PENSION'
        CHECK (retirement_fund_type IN ('PENSION', 'PROVIDENT', 'RETIREMENT_ANNUITY')),
    updated_by           UUID,
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ── 4. Certificates ──────────────────────────────────────────────────────────────────────────────
CREATE SEQUENCE hr_tax_certificate_seq;

CREATE TABLE hr_tax_certificates (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL REFERENCES tenants(id),
    tax_year           INT          NOT NULL,
    period             VARCHAR(10)  NOT NULL CHECK (period IN ('INTERIM', 'ANNUAL')),
    employee_id        UUID         NOT NULL REFERENCES hr_employees(id),
    certificate_number VARCHAR(30)  NOT NULL,
    certificate_type   VARCHAR(5)   NOT NULL CHECK (certificate_type IN ('IRP5', 'IT3A')),
    version            INT          NOT NULL DEFAULT 1,
    status             VARCHAR(12)  NOT NULL DEFAULT 'ISSUED' CHECK (status IN ('ISSUED', 'CANCELLED')),
    -- The employee and employer as they were when it was issued.
    employee_number    VARCHAR(30),
    first_names        VARCHAR(150) NOT NULL,
    surname            VARCHAR(150) NOT NULL,
    id_number          VARCHAR(20),
    tax_number         VARCHAR(20),
    date_of_birth      DATE,
    employer_paye_ref  VARCHAR(20)  NOT NULL,
    period_from        DATE         NOT NULL,
    period_to          DATE         NOT NULL,
    payslip_count      INT          NOT NULL,
    reason             VARCHAR(500),
    supersedes_id      UUID REFERENCES hr_tax_certificates(id),
    issued_by          UUID,
    issued_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    cancelled_by       UUID,
    cancelled_at       TIMESTAMP,
    cancel_reason      VARCHAR(500),
    released_by        UUID,
    released_at        TIMESTAMP
);
CREATE UNIQUE INDEX uq_hr_tax_certificate_number ON hr_tax_certificates (tenant_id, certificate_number);
-- One live certificate per employee and reconciliation.
CREATE UNIQUE INDEX uq_hr_tax_certificate_live ON hr_tax_certificates (tenant_id, tax_year, period, employee_id) WHERE status = 'ISSUED';
CREATE INDEX idx_hr_tax_certificates_employee ON hr_tax_certificates (tenant_id, employee_id);

CREATE TABLE hr_tax_certificate_lines (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID          NOT NULL REFERENCES tenants(id),
    certificate_id UUID          NOT NULL REFERENCES hr_tax_certificates(id),
    code           VARCHAR(4)    NOT NULL,
    description    VARCHAR(200)  NOT NULL,
    amount         NUMERIC(14,2) NOT NULL
);
CREATE INDEX idx_hr_tax_certificate_lines_cert ON hr_tax_certificate_lines (certificate_id);

CREATE OR REPLACE FUNCTION hr_tax_certificate_guard() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'A tax certificate cannot be deleted. Cancel it and issue a corrected one.';
    END IF;
    IF TG_TABLE_NAME = 'hr_tax_certificate_lines' THEN
        RAISE EXCEPTION 'The lines of an issued tax certificate cannot be changed.';
    END IF;
    -- Only the cancellation and release fields may change on a certificate.
    IF (to_jsonb(NEW) - 'status' - 'cancelled_by' - 'cancelled_at' - 'cancel_reason' - 'released_by' - 'released_at')
       IS DISTINCT FROM
       (to_jsonb(OLD) - 'status' - 'cancelled_by' - 'cancelled_at' - 'cancel_reason' - 'released_by' - 'released_at') THEN
        RAISE EXCEPTION 'An issued tax certificate cannot be changed. Cancel it and issue a corrected one.';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER hr_tax_certificates_guard BEFORE UPDATE OR DELETE ON hr_tax_certificates
    FOR EACH ROW EXECUTE FUNCTION hr_tax_certificate_guard();
CREATE TRIGGER hr_tax_certificate_lines_guard BEFORE UPDATE OR DELETE ON hr_tax_certificate_lines
    FOR EACH ROW EXECUTE FUNCTION hr_tax_certificate_guard();
