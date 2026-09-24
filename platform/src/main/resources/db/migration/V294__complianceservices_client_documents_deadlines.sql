-- src/main/resources/db/migration/V294__complianceservices_client_documents_deadlines.sql
--
-- Phase 3 of complianceservices: client-scoped documents and deadlines,
-- following the exact "parallel entity, same validation shape" pattern
-- V293's client_compliance_registrations already established. See
-- ClientComplianceDocument.java and ClientComplianceDeadline.java for
-- the full reasoning — same shape as compliancetender's own
-- ComplianceDocument/ComplianceDeadline, deliberately independent
-- tables, not shared schema, for the reasons ClientComplianceRegistration's
-- own Javadoc already documents in full.

CREATE TABLE client_compliance_documents (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    client_id             UUID NOT NULL REFERENCES compliance_service_clients(id),
    registration_id       UUID REFERENCES client_compliance_registrations(id), -- nullable
    document_type         VARCHAR(60) NOT NULL,
    evidence_id           UUID NOT NULL,  -- references evidence's own table — not a DB FK, evidence lives in another module
    issue_date            DATE,
    expiry_date            DATE,
    verified_by           UUID,
    verified_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by            UUID,
    version               BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_client_compliance_doc_tenant ON client_compliance_documents(tenant_id);
CREATE INDEX idx_client_compliance_doc_client ON client_compliance_documents(client_id);
CREATE INDEX idx_client_compliance_doc_registration ON client_compliance_documents(registration_id);

CREATE TABLE client_compliance_deadlines (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    client_id             UUID NOT NULL REFERENCES compliance_service_clients(id),
    registration_id       UUID REFERENCES client_compliance_registrations(id), -- nullable
    deadline_type         VARCHAR(60) NOT NULL,
    description           VARCHAR(500),
    due_date              DATE NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, DONE, MISSED
    completed_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by            UUID
);
CREATE INDEX idx_client_compliance_deadline_tenant ON client_compliance_deadlines(tenant_id);
CREATE INDEX idx_client_compliance_deadline_client ON client_compliance_deadlines(client_id);
CREATE INDEX idx_client_compliance_deadline_due_sweep ON client_compliance_deadlines(due_date) WHERE status = 'PENDING';
