-- A job can have several vacancies and carries the company's own requisition reference. Existing jobs keep one vacancy.
ALTER TABLE rec_jobs ADD COLUMN IF NOT EXISTS vacancies INT NOT NULL DEFAULT 1;
ALTER TABLE rec_jobs ADD COLUMN IF NOT EXISTS requisition_ref VARCHAR(40);
ALTER TABLE rec_jobs ADD CONSTRAINT chk_rec_jobs_vacancies CHECK (vacancies BETWEEN 1 AND 500);

-- Each saved wording of a job, so what was advertised on a given day can be shown.
CREATE TABLE IF NOT EXISTS rec_job_versions (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    job_id        UUID NOT NULL REFERENCES rec_jobs(id) ON DELETE CASCADE,
    version_no    INT  NOT NULL,
    title         VARCHAR(255) NOT NULL,
    description   TEXT NOT NULL,
    requirements  TEXT,
    benefits      TEXT,
    salary_min    NUMERIC(12,2),
    salary_max    NUMERIC(12,2),
    changed_by    UUID,
    changed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rec_job_version UNIQUE (job_id, version_no)
);
-- The wording each existing job has today becomes version 1.
INSERT INTO rec_job_versions (id, tenant_id, job_id, version_no, title, description, requirements, benefits, salary_min, salary_max, changed_by, changed_at)
SELECT gen_random_uuid(), tenant_id, id, 1, title, description, requirements, benefits, salary_min, salary_max, created_by, COALESCE(created_at, NOW())
FROM rec_jobs ON CONFLICT DO NOTHING;
