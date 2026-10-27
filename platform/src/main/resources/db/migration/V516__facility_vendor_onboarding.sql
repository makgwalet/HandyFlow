-- Vendor onboarding and compliance documents for the internal facilities module.
-- Vendors that already exist are treated as APPROVED so nothing already in use stops working; new vendors start PENDING
-- (the application sets the value explicitly on insert).

ALTER TABLE facility_vendors ADD COLUMN IF NOT EXISTS onboarding_status  VARCHAR(20) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE facility_vendors ADD COLUMN IF NOT EXISTS approved_by_user_id UUID;
ALTER TABLE facility_vendors ADD COLUMN IF NOT EXISTS approved_at         TIMESTAMPTZ;
ALTER TABLE facility_vendors ADD COLUMN IF NOT EXISTS suspended_reason    VARCHAR(500);
ALTER TABLE facility_vendors ADD COLUMN IF NOT EXISTS created_by_user_id  UUID;

CREATE TABLE IF NOT EXISTS facility_vendor_documents (
    id             UUID PRIMARY KEY,
    tenant_id      UUID          NOT NULL,
    vendor_id      UUID          NOT NULL,
    doc_type       VARCHAR(40)   NOT NULL,
    reference      VARCHAR(120),
    issued_by      VARCHAR(200),
    issue_date     DATE,
    expiry_date    DATE,
    cover_amount   NUMERIC(15,2),
    document_ref   VARCHAR(500),
    status         VARCHAR(20)   NOT NULL DEFAULT 'VALID',
    revoked_reason VARCHAR(500),
    revoked_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    version        BIGINT
);
CREATE INDEX IF NOT EXISTS idx_facility_vendor_docs_vendor ON facility_vendor_documents (tenant_id, vendor_id);
CREATE INDEX IF NOT EXISTS idx_facility_vendor_docs_expiry ON facility_vendor_documents (expiry_date) WHERE status = 'VALID';
