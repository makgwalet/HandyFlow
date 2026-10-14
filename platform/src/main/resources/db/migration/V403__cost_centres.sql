-- Finance F24: cost centres, departments, branches and projects on journal lines.
-- One list of "dimensions" with a kind, optional parent (a department inside a branch, a project inside a customer branch),
-- and a nullable tag on each journal line. Untagged lines stay valid and show as "Unallocated" in reports.
CREATE TABLE acc_cost_centres (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(120) NOT NULL,
    kind        VARCHAR(20)  NOT NULL DEFAULT 'COST_CENTRE'
                CHECK (kind IN ('COST_CENTRE','DEPARTMENT','BRANCH','PROJECT')),
    parent_id   UUID REFERENCES acc_cost_centres(id),
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_by  UUID,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_cost_centre_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id)
);
CREATE UNIQUE INDEX uq_acc_cost_centres_code ON acc_cost_centres (tenant_id, upper(code));
CREATE INDEX idx_acc_cost_centres_parent ON acc_cost_centres (parent_id);

ALTER TABLE acc_journal_lines ADD COLUMN cost_centre_id UUID REFERENCES acc_cost_centres(id);
CREATE INDEX idx_acc_journal_lines_cost_centre ON acc_journal_lines (tenant_id, cost_centre_id) WHERE cost_centre_id IS NOT NULL;
