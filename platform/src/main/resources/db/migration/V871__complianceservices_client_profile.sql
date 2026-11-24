-- Compliance Services: a fuller client profile, plus a record of where each
-- profile fact came from (supplied by the client, read from a document,
-- checked against a register) and who confirmed it.

ALTER TABLE compliance_service_clients
    ADD COLUMN IF NOT EXISTS legal_name          VARCHAR(255),
    ADD COLUMN IF NOT EXISTS trading_name        VARCHAR(255),
    ADD COLUMN IF NOT EXISTS registration_number VARCHAR(50),
    ADD COLUMN IF NOT EXISTS entity_type         VARCHAR(30),
    ADD COLUMN IF NOT EXISTS tax_number          VARCHAR(30),
    ADD COLUMN IF NOT EXISTS vat_number          VARCHAR(30),
    ADD COLUMN IF NOT EXISTS registered_address  TEXT,
    ADD COLUMN IF NOT EXISTS postal_address      TEXT,
    ADD COLUMN IF NOT EXISTS industry            VARCHAR(120),
    ADD COLUMN IF NOT EXISTS employee_count      INTEGER,
    ADD COLUMN IF NOT EXISTS turnover_band       VARCHAR(30),
    ADD COLUMN IF NOT EXISTS risk_rating         VARCHAR(10),
    ADD COLUMN IF NOT EXISTS onboarding_status   VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED';

-- One client cannot be on file twice under the same registration number.
CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_clients_tenant_reg_no
    ON compliance_service_clients(tenant_id, registration_number)
    WHERE registration_number IS NOT NULL;

CREATE TABLE IF NOT EXISTS compliance_client_field_sources (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    client_id     UUID NOT NULL REFERENCES compliance_service_clients(id) ON DELETE CASCADE,
    field_name    VARCHAR(40) NOT NULL,
    source        VARCHAR(20) NOT NULL,   -- CLIENT_SUPPLIED, STAFF_ENTERED, DOCUMENT, REGISTER_CHECK
    method        VARCHAR(200),           -- how it was obtained or checked
    reference     VARCHAR(200),           -- document name, register reference, etc.
    source_date   DATE,                   -- date of the source itself
    recorded_by   UUID,
    recorded_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    verified_by   UUID,                   -- null until a person other than the one who entered it confirms
    verified_at   TIMESTAMPTZ,
    verification_exception_reason TEXT,   -- required when the person who entered it also confirms it
    version       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_compliance_client_field UNIQUE (client_id, field_name)
);
CREATE INDEX IF NOT EXISTS idx_compliance_client_field_sources_tenant
    ON compliance_client_field_sources(tenant_id, client_id);
