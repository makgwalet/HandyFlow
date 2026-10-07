-- V336__security_readiness_settings.sql
--
-- Per-tenant choice of which screenings and guard-file documents count towards a guard's deployment readiness.
-- One row per tenant; until one is saved the defaults apply (criminal record check, reference check and drug test;
-- ID copy). A PSiRA registration is always required: it is a legal requirement, not a company choice.
-- Values are comma-separated enum names (GuardScreeningRecord.ScreeningType, GuardDocument.Category).

CREATE TABLE security_readiness_settings (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL UNIQUE,
    required_screening  VARCHAR(500) NOT NULL DEFAULT 'CRIMINAL_RECORD_CHECK,REFERENCE_CHECK,DRUG_TEST',
    required_documents  VARCHAR(500) NOT NULL DEFAULT 'ID_COPY',
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by_name     VARCHAR(200)
);

COMMENT ON TABLE security_readiness_settings IS
    'Which screenings and guard-file documents a tenant requires for deployment readiness. Defaults apply until a row exists.';
