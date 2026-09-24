-- src/main/resources/db/migration/V293__complianceservices_client_registrations.sql
--
-- Phase 2 of complianceservices: per-client compliance registration
-- tracking. Deliberately a PARALLEL table, not a client_id column added
-- onto compliancetender's own compliance_registrations — see
-- ClientComplianceRegistration.java's own Javadoc for the full reasoning
-- (compliancetender cannot depend on complianceservices without a
-- circular module dependency, and retrofitting a client dimension onto
-- an already-shipped, tested table for a different-shaped use case is a
-- larger, riskier change than this module owning its own client-scoped
-- table). Structurally similar to compliance_registrations by design —
-- same fields, same expiry semantics — but genuinely independent, not
-- shared schema.

CREATE TABLE client_compliance_registrations (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,           -- the SERVICE PROVIDER's own tenant
    client_id             UUID NOT NULL REFERENCES compliance_service_clients(id),
    authority             VARCHAR(20) NOT NULL,    -- CIPC, SARS, UIF, PSIRA, CSD, CIDB, NHBRC, OTHER
    registration_type     VARCHAR(60) NOT NULL,
    registration_number   VARCHAR(100),
    status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, EXPIRED, LAPSED, PENDING, NOT_APPLICABLE
    issued_date           DATE,
    expiry_date           DATE,
    notes                 TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by            UUID,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_client_compliance_reg_expiry_after_issue
        CHECK (expiry_date IS NULL OR issued_date IS NULL OR expiry_date >= issued_date)
);
CREATE INDEX idx_client_compliance_reg_tenant ON client_compliance_registrations(tenant_id);
CREATE INDEX idx_client_compliance_reg_client ON client_compliance_registrations(client_id);
CREATE INDEX idx_client_compliance_reg_expiry_sweep ON client_compliance_registrations(expiry_date) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uq_client_compliance_reg_client_authority_type
    ON client_compliance_registrations(client_id, authority, registration_type);
