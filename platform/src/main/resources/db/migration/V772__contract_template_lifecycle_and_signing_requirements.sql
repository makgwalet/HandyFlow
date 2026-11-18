-- V772: Contracting, approved decisions 4 and 5 (18 Nov 2026).
--  4. Template lifecycle DRAFT > INTERNAL_REVIEW > LEGAL_REVIEW > APPROVED > PUBLISHED > SUPERSEDED, and a legal review record
--     per template version.
--  5. A signature requirement on templates and contracts, so a document that needs more than a simple electronic signature
--     cannot silently go out for OTP signing.
-- Existing templates stay PUBLISHED (so nothing breaks) and show as NOT_REVIEWED until a legal review is recorded.
-- Runs once (Flyway). Do not re-run by hand: it marks every DRAFT template without a published date as PUBLISHED.

ALTER TABLE contract_templates
    ADD COLUMN IF NOT EXISTS family_id               UUID,
    ADD COLUMN IF NOT EXISTS status                  VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN IF NOT EXISTS published_at            TIMESTAMP,
    ADD COLUMN IF NOT EXISTS superseded_at           TIMESTAMP,
    ADD COLUMN IF NOT EXISTS legal_area              VARCHAR(100),
    ADD COLUMN IF NOT EXISTS jurisdiction            VARCHAR(100) NOT NULL DEFAULT 'South Africa',
    ADD COLUMN IF NOT EXISTS required_signing_method VARCHAR(30);

UPDATE contract_templates SET family_id = id WHERE family_id IS NULL;
-- Everything that exists today was usable, so it stays usable.
UPDATE contract_templates SET status = 'PUBLISHED', published_at = COALESCE(published_at, created_at)
 WHERE status = 'DRAFT' AND published_at IS NULL;

ALTER TABLE contract_templates DROP CONSTRAINT IF EXISTS ck_contract_templates_status;
ALTER TABLE contract_templates ADD CONSTRAINT ck_contract_templates_status
    CHECK (status IN ('DRAFT', 'INTERNAL_REVIEW', 'LEGAL_REVIEW', 'APPROVED', 'PUBLISHED', 'SUPERSEDED'));
ALTER TABLE contract_templates DROP CONSTRAINT IF EXISTS ck_contract_templates_signing_method;
ALTER TABLE contract_templates ADD CONSTRAINT ck_contract_templates_signing_method
    CHECK (required_signing_method IS NULL
           OR required_signing_method IN ('SIMPLE_ELECTRONIC', 'ADVANCED_ELECTRONIC', 'WET_INK', 'IN_PERSON'));

CREATE INDEX IF NOT EXISTS idx_contract_templates_family ON contract_templates (tenant_id, family_id);

-- One row per recorded legal review of one template version. Evidence of review at a point in time, not a guarantee that
-- every contract made from the template is lawful or suitable for every transaction.
CREATE TABLE IF NOT EXISTS contract_template_legal_reviews (
    id                           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                    UUID NOT NULL,
    template_id                  UUID NOT NULL REFERENCES contract_templates(id),
    template_version             INT  NOT NULL,
    reviewer_name                VARCHAR(200) NOT NULL,
    reviewer_professional_details VARCHAR(300) NOT NULL,   -- e.g. admitted attorney, Law Society number
    reviewer_organisation        VARCHAR(200),
    review_date                  DATE NOT NULL,
    legal_area                   VARCHAR(100) NOT NULL,
    jurisdiction                 VARCHAR(100) NOT NULL DEFAULT 'South Africa',
    scope_assumptions            TEXT,
    outstanding_issues           TEXT,
    outcome                      VARCHAR(20) NOT NULL,
    next_review_date             DATE,
    evidence_document_id         UUID,                      -- signed-off review document, stored in Documents
    recorded_by                  UUID,
    withdrawn_at                 TIMESTAMP,
    withdrawn_reason             VARCHAR(500),
    withdrawn_by                 UUID,
    created_at                   TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT ck_template_legal_review_outcome CHECK (outcome IN ('APPROVED', 'REJECTED', 'CONDITIONAL'))
);
CREATE INDEX IF NOT EXISTS idx_template_legal_reviews_template ON contract_template_legal_reviews (template_id, template_version);

-- Contracts carry the signature standard their template (or the staff member) requires.
ALTER TABLE contracts ADD COLUMN IF NOT EXISTS required_signing_method VARCHAR(30);
ALTER TABLE contracts DROP CONSTRAINT IF EXISTS ck_contracts_signing_method;
ALTER TABLE contracts ADD CONSTRAINT ck_contracts_signing_method
    CHECK (required_signing_method IS NULL
           OR required_signing_method IN ('SIMPLE_ELECTRONIC', 'ADVANCED_ELECTRONIC', 'WET_INK', 'IN_PERSON'));

-- How each signature was made. Everything signed so far was OTP plus drawn signature.
ALTER TABLE contract_signatures ADD COLUMN IF NOT EXISTS signing_method VARCHAR(30) NOT NULL DEFAULT 'SIMPLE_ELECTRONIC';
