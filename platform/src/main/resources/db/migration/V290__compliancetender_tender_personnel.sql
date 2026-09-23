-- src/main/resources/db/migration/V290__compliancetender_tender_personnel.sql
--
-- The first real cross-module reference in compliancetender — links a
-- tender to an HR employee record by id only. No name, qualification, or
-- experience data is duplicated here; that's looked up live from HrFacade
-- whenever this is read (see TenderPersonnel.java's own Javadoc for why).

CREATE TABLE tender_personnel (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    tender_id    UUID NOT NULL REFERENCES tenders(id),
    employee_id  UUID NOT NULL,  -- references hr's own employee table — not a DB FK, HR lives in another module
    role         VARCHAR(100) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID
);
CREATE INDEX idx_tender_personnel_tenant ON tender_personnel(tenant_id);
CREATE INDEX idx_tender_personnel_tender ON tender_personnel(tender_id);
-- Same person shouldn't be added to the same tender twice with the same role
CREATE UNIQUE INDEX uq_tender_personnel_tender_employee_role ON tender_personnel(tender_id, employee_id, role);
