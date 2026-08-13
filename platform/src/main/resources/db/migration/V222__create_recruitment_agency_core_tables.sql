-- Core domain: requisitions, candidates, placements, stage history.
-- Second migration for this module — run after
-- V_create_recruitment_agency_tables.sql (practice shell + client
-- portfolio), matching how Payroll Bureau's migrations were sequenced.

CREATE TABLE reca_requisitions (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL,
    client_id            UUID NOT NULL REFERENCES reca_agency_clients(id),
    requisition_number   VARCHAR(30) NOT NULL,
    title                VARCHAR(255) NOT NULL,
    description          TEXT,
    salary_min           NUMERIC(15,2),
    salary_max           NUMERIC(15,2),
    location             VARCHAR(255),
    employment_type      VARCHAR(20) NOT NULL DEFAULT 'PERMANENT',
    status               VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    target_start_date    DATE,
    notes                TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    filled_at            TIMESTAMPTZ
);
CREATE INDEX idx_reca_requisitions_client ON reca_requisitions (client_id);
CREATE INDEX idx_reca_requisitions_tenant_status ON reca_requisitions (tenant_id, status);

CREATE TABLE reca_candidates (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    full_name         VARCHAR(255) NOT NULL,
    email             VARCHAR(255),
    phone             VARCHAR(50),
    current_title     VARCHAR(255),
    current_employer  VARCHAR(255),
    skills            TEXT,
    source            VARCHAR(30),
    cv_file_name      VARCHAR(255),
    cv_storage_key    VARCHAR(500),
    notes             TEXT,
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reca_candidates_tenant ON reca_candidates (tenant_id);

CREATE TABLE reca_placements (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID NOT NULL,
    requisition_id         UUID NOT NULL REFERENCES reca_requisitions(id),
    candidate_id           UUID NOT NULL REFERENCES reca_candidates(id),
    client_id              UUID NOT NULL REFERENCES reca_agency_clients(id),
    stage                  VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
    offered_salary         NUMERIC(15,2),
    placement_fee_amount   NUMERIC(15,2),
    placed_at              TIMESTAMPTZ,
    guarantee_ends_at      DATE,
    notes                  TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_reca_placement_candidate_requisition UNIQUE (requisition_id, candidate_id)
    -- one candidate can only be submitted once per requisition — a
    -- second attempt should be a stage change on the existing row, not
    -- a duplicate placement
);
CREATE INDEX idx_reca_placements_requisition ON reca_placements (requisition_id);
CREATE INDEX idx_reca_placements_candidate ON reca_placements (candidate_id);
CREATE INDEX idx_reca_placements_client ON reca_placements (client_id);
CREATE INDEX idx_reca_placements_tenant_stage ON reca_placements (tenant_id, stage);

CREATE TABLE reca_placement_stage_history (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    placement_id  UUID NOT NULL REFERENCES reca_placements(id),
    from_stage    VARCHAR(30),
    to_stage      VARCHAR(30) NOT NULL,
    notes         TEXT,
    changed_by    UUID,
    changed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reca_stage_history_placement ON reca_placement_stage_history (placement_id);