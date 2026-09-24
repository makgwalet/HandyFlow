-- src/main/resources/db/migration/V298__complianceservices_client_tender_personnel.sql
--
-- Resolves the open personnel-reference design question from Phase 5:
-- a client-scoped tender's key personnel are the SERVICE PROVIDER's own
-- staff, referenced from the SAME tenant's hr records
-- compliancetender.TenderPersonnel already references — not a separate,
-- client-tracked personnel concept. See ClientTenderPersonnel.java for
-- the full reasoning.

CREATE TABLE client_tender_personnel (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    client_tender_id  UUID NOT NULL REFERENCES client_tenders(id),
    employee_id       UUID NOT NULL,  -- references hr's own employee table — not a DB FK, HR lives in another module
    role              VARCHAR(100) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID
);
CREATE INDEX idx_client_tender_personnel_tenant ON client_tender_personnel(tenant_id);
CREATE INDEX idx_client_tender_personnel_tender ON client_tender_personnel(client_tender_id);
CREATE UNIQUE INDEX uq_client_tender_personnel_tender_employee_role
    ON client_tender_personnel(client_tender_id, employee_id, role);
