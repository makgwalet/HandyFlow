-- V312__requirement_evidence_rules.sql
--
-- Business readiness (ADR-003): a tracked requirement can say what satisfies it, so a tender requirement can be evaluated against the registrations and documents the business
-- actually holds, instead of only being ticked by hand.
--   * the registration that satisfies it: authority and/or registration type (matched case-insensitively against the business's registrations; either can be blank = any)
--   * the document that satisfies it is the EXISTING evidence_type column ("expected compliance_documents.document_type this requirement is satisfied by"), so no column is added for it
-- This is configuration, not code: regulatory knowledge (what proves "CSD active", what proves "tax compliant") belongs to the tenant's catalogue and can change without a release.
-- Nullable and additive: a requirement with neither set is simply "not evaluated", exactly as before.
ALTER TABLE compliance_requirements
    ADD COLUMN satisfied_by_authority          VARCHAR(20),
    ADD COLUMN satisfied_by_registration_type  VARCHAR(60);

ALTER TABLE client_compliance_requirements
    ADD COLUMN satisfied_by_authority          VARCHAR(20),
    ADD COLUMN satisfied_by_registration_type  VARCHAR(60);
