-- HR: passports and work permits for employees without a South African ID, and the passport on the tax certificate.

ALTER TABLE hr_employees
    ADD COLUMN IF NOT EXISTS passport_number    VARCHAR(20),
    ADD COLUMN IF NOT EXISTS passport_country   VARCHAR(2),
    ADD COLUMN IF NOT EXISTS passport_expiry    DATE,
    ADD COLUMN IF NOT EXISTS work_permit_type   VARCHAR(30),
    ADD COLUMN IF NOT EXISTS work_permit_number VARCHAR(40),
    ADD COLUMN IF NOT EXISTS work_permit_expiry DATE;

CREATE INDEX IF NOT EXISTS idx_hr_employees_passport
    ON hr_employees (tenant_id, passport_country, passport_number) WHERE passport_number IS NOT NULL AND deleted_at IS NULL;

-- Expiry sweep: only people who still work here and have something that can lapse.
CREATE INDEX IF NOT EXISTS idx_hr_employees_permit_expiry
    ON hr_employees (work_permit_expiry) WHERE work_permit_expiry IS NOT NULL AND deleted_at IS NULL;

-- A certificate keeps what the employee's record said when it was issued, as it already does for the ID number.
ALTER TABLE hr_tax_certificates
    ADD COLUMN IF NOT EXISTS passport_number  VARCHAR(20),
    ADD COLUMN IF NOT EXISTS passport_country VARCHAR(2);
