-- Training: which certificates a role must hold, and which sessions people may book themselves onto.

-- A requirement says: people with this job title and/or in this department must hold a valid certificate for this course.
-- Both left empty means everyone. Titles and departments are compared ignoring case and surrounding spaces.
CREATE TABLE training_requirements (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    course_id   UUID NOT NULL REFERENCES training_courses(id),
    job_title   VARCHAR(150),
    department  VARCHAR(150),
    created_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_training_req_title CHECK (job_title IS NULL OR btrim(job_title) <> ''),
    CONSTRAINT chk_training_req_dept  CHECK (department IS NULL OR btrim(department) <> '')
);
CREATE UNIQUE INDEX uq_training_requirement
    ON training_requirements (tenant_id, course_id, lower(coalesce(job_title, '')), lower(coalesce(department, '')));
CREATE INDEX idx_training_requirements_tenant ON training_requirements (tenant_id);

-- Employees can only book sessions an administrator has opened for it.
ALTER TABLE training_sessions ADD COLUMN open_booking BOOLEAN NOT NULL DEFAULT FALSE;
