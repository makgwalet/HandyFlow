-- Checks to complete on a candidate before they are hired: identity and right to work, qualifications, references, background.
CREATE TABLE IF NOT EXISTS rec_verifications (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    application_id UUID NOT NULL REFERENCES rec_applications(id) ON DELETE CASCADE,
    check_type     VARCHAR(20) NOT NULL CHECK (check_type IN ('IDENTITY','RIGHT_TO_WORK','QUALIFICATION','REFERENCE','BACKGROUND','OTHER')),
    description    VARCHAR(200) NOT NULL,
    required       BOOLEAN NOT NULL DEFAULT TRUE,
    status         VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','VERIFIED','FAILED','WAIVED')),
    result_note    VARCHAR(500),
    requested_by   UUID,
    requested_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    decided_by     UUID,
    decided_at     TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_rec_verifications_app ON rec_verifications (application_id);

-- The reason for a rejection (or a declined / withdrawn offer) is accepted up to 500 characters by the application, but the original column held 255.
ALTER TABLE rec_applications ALTER COLUMN rejection_reason TYPE VARCHAR(500);
