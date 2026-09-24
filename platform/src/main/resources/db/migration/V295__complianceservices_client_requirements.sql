-- src/main/resources/db/migration/V295__complianceservices_client_requirements.sql
--
-- Phase 4 of complianceservices: the client-scoped requirement catalogue,
-- following the same "parallel entity" pattern V293/V294 established.
-- See ClientComplianceRequirement.java for the full reasoning — same
-- versioning discipline as compliancetender.ComplianceRequirement (a
-- past readiness check should be judged against the rules actually in
-- force at the time, not silently reinterpreted against today's), same
-- reason there is deliberately no DELETE for this entity either.

CREATE TABLE client_compliance_requirements (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    client_id             UUID NOT NULL REFERENCES compliance_service_clients(id),
    code                  VARCHAR(60) NOT NULL,
    name                  VARCHAR(255) NOT NULL,
    applies_to            VARCHAR(60),
    evidence_type         VARCHAR(60),
    required              BOOLEAN NOT NULL DEFAULT TRUE,
    requirement_version   INTEGER NOT NULL DEFAULT 1,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by            UUID
);
CREATE INDEX idx_client_compliance_req_tenant ON client_compliance_requirements(tenant_id);
CREATE INDEX idx_client_compliance_req_client ON client_compliance_requirements(client_id);
CREATE UNIQUE INDEX uq_client_compliance_req_client_code_version
    ON client_compliance_requirements(client_id, code, requirement_version);
