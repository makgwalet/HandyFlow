-- Compliance Services: work given to an outside provider for a client. Work cannot start until a conflict-of-interest declaration is
-- recorded (and approved by someone else if a conflict is declared), and the provider holds a checked accreditation unless the practice says
-- why none is needed. A deliverable is reviewed by someone other than the person who received it. Nothing is deleted.

CREATE TABLE IF NOT EXISTS compliance_provider_assignments (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    provider_id                 UUID NOT NULL REFERENCES compliance_providers(id),
    client_id                   UUID NOT NULL REFERENCES compliance_service_clients(id),
    title                       VARCHAR(200) NOT NULL,
    description                 TEXT,
    service_code                VARCHAR(60),
    due_date                    DATE,
    status                      VARCHAR(10) NOT NULL DEFAULT 'PROPOSED',
    accreditation_required      BOOLEAN NOT NULL DEFAULT TRUE,
    waiver_note                 TEXT,
    conflict_status             VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    conflict_note               TEXT,
    conflict_by                 UUID,
    conflict_at                 TIMESTAMPTZ,
    conflict_approved_by        UUID,
    conflict_approved_at        TIMESTAMPTZ,
    conflict_approval_note      TEXT,
    activated_by                UUID,
    activated_at                TIMESTAMPTZ,
    completed_on                DATE,
    completed_by                UUID,
    rating INTEGER,
    completion_note             TEXT,
    on_time                     BOOLEAN,
    cancelled_by                UUID,
    cancelled_at                TIMESTAMPTZ,
    cancel_note                 TEXT,
    created_by                  UUID NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_cs_prov_assign_status CHECK (status IN ('PROPOSED', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_cs_prov_assign_conflict CHECK (conflict_status IN ('PENDING', 'NONE', 'DECLARED')),
    CONSTRAINT ck_cs_prov_assign_conflict_recorded CHECK ((conflict_status = 'PENDING') = (conflict_by IS NULL)),
    CONSTRAINT ck_cs_prov_assign_conflict_note CHECK (conflict_status = 'PENDING' OR (conflict_note IS NOT NULL AND length(btrim(conflict_note)) >= 10)),
    CONSTRAINT ck_cs_prov_assign_approver CHECK (conflict_approved_by IS NULL OR (conflict_status = 'DECLARED' AND conflict_approved_by <> conflict_by
        AND conflict_approval_note IS NOT NULL AND length(btrim(conflict_approval_note)) >= 10)),
    CONSTRAINT ck_cs_prov_assign_cleared CHECK (status NOT IN ('ACTIVE', 'COMPLETED') OR conflict_status = 'NONE' OR (conflict_status = 'DECLARED' AND conflict_approved_by IS NOT NULL)),
    CONSTRAINT ck_cs_prov_assign_waiver CHECK (accreditation_required OR (waiver_note IS NOT NULL AND length(btrim(waiver_note)) >= 10)),
    CONSTRAINT ck_cs_prov_assign_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
    CONSTRAINT ck_cs_prov_assign_completed CHECK ((status = 'COMPLETED') = (completed_on IS NOT NULL AND completed_by IS NOT NULL AND rating IS NOT NULL AND on_time IS NOT NULL
        AND completion_note IS NOT NULL AND length(btrim(completion_note)) >= 10)),
    CONSTRAINT ck_cs_prov_assign_cancelled CHECK ((status = 'CANCELLED') = (cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL AND cancel_note IS NOT NULL AND length(btrim(cancel_note)) >= 10))
);
CREATE INDEX IF NOT EXISTS idx_cs_prov_assign_client ON compliance_provider_assignments(tenant_id, client_id);
CREATE INDEX IF NOT EXISTS idx_cs_prov_assign_provider ON compliance_provider_assignments(tenant_id, provider_id);
CREATE INDEX IF NOT EXISTS idx_cs_prov_assign_status ON compliance_provider_assignments(tenant_id, status);

CREATE TABLE IF NOT EXISTS compliance_provider_deliverables (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    assignment_id  UUID NOT NULL REFERENCES compliance_provider_assignments(id),
    title          VARCHAR(200) NOT NULL,
    due_date       DATE,
    status         VARCHAR(10) NOT NULL DEFAULT 'EXPECTED',
    received_by    UUID,
    received_at    TIMESTAMPTZ,
    reviewed_by    UUID,
    reviewed_at    TIMESTAMPTZ,
    review_note    TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_cs_prov_deliv_status CHECK (status IN ('EXPECTED', 'RECEIVED', 'ACCEPTED', 'REJECTED', 'WAIVED')),
    CONSTRAINT ck_cs_prov_deliv_received CHECK ((status IN ('RECEIVED', 'ACCEPTED', 'REJECTED')) = (received_by IS NOT NULL AND received_at IS NOT NULL) OR status = 'WAIVED'),
    CONSTRAINT ck_cs_prov_deliv_reviewed CHECK ((status IN ('ACCEPTED', 'REJECTED', 'WAIVED')) = (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND review_note IS NOT NULL AND length(btrim(review_note)) >= 10)),
    CONSTRAINT ck_cs_prov_deliv_other_person CHECK (status NOT IN ('ACCEPTED', 'REJECTED') OR reviewed_by <> received_by)
);
CREATE INDEX IF NOT EXISTS idx_cs_prov_deliv_assignment ON compliance_provider_deliverables(tenant_id, assignment_id);
