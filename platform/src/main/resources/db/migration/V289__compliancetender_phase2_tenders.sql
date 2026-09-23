-- src/main/resources/db/migration/V289__compliancetender_phase2_tenders.sql
--
-- Phase 2 of compliancetender: the core tender record and its requirement
-- matrix. Deliberately no reference tables yet to Projects/HR/Fleet/
-- Accounting — that's a following step, not this one (see Tender.java's
-- own Javadoc and the strategic roadmap backlog, Part 6).

CREATE TABLE tenders (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
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
CREATE INDEX idx_tenders_tenant ON tenders(tenant_id);
CREATE INDEX idx_tenders_tenant_status ON tenders(tenant_id, status);
CREATE INDEX idx_tenders_closing_date ON tenders(closing_date) WHERE status NOT IN ('AWARDED', 'UNSUCCESSFUL', 'WITHDRAWN');
CREATE UNIQUE INDEX uq_tenders_tenant_number ON tenders(tenant_id, tender_number);

CREATE TABLE tender_requirements (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    tender_id                   UUID NOT NULL REFERENCES tenders(id),
    compliance_requirement_id   UUID REFERENCES compliance_requirements(id), -- nullable
    description                 VARCHAR(500) NOT NULL,
    source                      VARCHAR(20) NOT NULL DEFAULT 'MANUAL', -- COMPLIANCE, PROJECTS, HR, FLEET, ACCOUNTING, MANUAL
    status                      VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW', -- MET, MISSING, NOT_APPLICABLE, PENDING_REVIEW
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                  UUID,
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by                  UUID
);
CREATE INDEX idx_tender_req_tenant ON tender_requirements(tenant_id);
CREATE INDEX idx_tender_req_tender ON tender_requirements(tender_id);

-- Permissions: reuses the compliancetender module registered in V288 —
-- same COMPLIANCE_READ/MANAGE/ADMIN authorities, no new permission rows
-- needed since tenders are just another kind of thing this module
-- manages, not a separately-subscribable capability.
