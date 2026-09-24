-- src/main/resources/db/migration/V296__complianceservices_client_tenders.sql
--
-- Phase 5 of complianceservices: the largest remaining piece — a
-- client-scoped tender workspace, mirroring compliancetender's own
-- Tender/TenderRequirement exactly (same fields, same lifecycle, same
-- transition table), following the same parallel-entity pattern V293-
-- V295 already established. See ClientTender.java for the full
-- reasoning. Numbering uses TenantNumberingFacade (documentType
-- "CLIENT_TENDER", default code "CTND") — a distinct code from
-- compliancetender's own "TND", since these are numbered independently
-- and mixing the two prefixes would be confusing.

CREATE TABLE client_tenders (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    client_id                   UUID NOT NULL REFERENCES compliance_service_clients(id),
    tender_number               VARCHAR(60) NOT NULL,
    name                        VARCHAR(255) NOT NULL,
    tender_authority            VARCHAR(255),
    authority_reference_number  VARCHAR(100),
    closing_date                DATE,
    briefing_date               DATE,
    site_inspection_date        DATE,
    estimated_value             NUMERIC(15,2),
    industry                    VARCHAR(100),
    required_class_of_work      VARCHAR(100),
    status                      VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    outcome_reason              TEXT,
    awarded_value               NUMERIC(15,2),
    submitted_at                TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                  UUID,
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by                  UUID,
    version                     BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_client_tenders_tenant ON client_tenders(tenant_id);
CREATE INDEX idx_client_tenders_client ON client_tenders(client_id);
CREATE INDEX idx_client_tenders_tenant_status ON client_tenders(tenant_id, status);
CREATE INDEX idx_client_tenders_closing_date ON client_tenders(closing_date) WHERE status NOT IN ('AWARDED', 'UNSUCCESSFUL', 'WITHDRAWN');
CREATE UNIQUE INDEX uq_client_tenders_tenant_number ON client_tenders(tenant_id, tender_number);

CREATE TABLE client_tender_requirements (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    client_tender_id            UUID NOT NULL REFERENCES client_tenders(id),
    client_requirement_id       UUID REFERENCES client_compliance_requirements(id), -- nullable
    description                 VARCHAR(500) NOT NULL,
    source                      VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    status                      VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                  UUID,
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by                  UUID
);
CREATE INDEX idx_client_tender_req_tenant ON client_tender_requirements(tenant_id);
CREATE INDEX idx_client_tender_req_tender ON client_tender_requirements(client_tender_id);
