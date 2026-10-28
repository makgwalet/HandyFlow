-- Recruiter: what it cost to fill a job (adverts, agency fees, assessments, checks), so a report can say what a hire cost.
CREATE TABLE rec_job_costs (
    id          UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id   UUID          NOT NULL,
    job_id      UUID          NOT NULL REFERENCES rec_jobs (id) ON DELETE CASCADE,
    category    VARCHAR(20)   NOT NULL CHECK (category IN ('ADVERTISING', 'AGENCY_FEE', 'ASSESSMENT', 'BACKGROUND_CHECK', 'OTHER')),
    amount      NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    incurred_on DATE          NOT NULL,
    note        VARCHAR(200),
    created_by  UUID,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_rec_job_costs PRIMARY KEY (id)
);
CREATE INDEX idx_rec_job_costs_job ON rec_job_costs (job_id);
CREATE INDEX idx_rec_job_costs_tenant_date ON rec_job_costs (tenant_id, incurred_on);
