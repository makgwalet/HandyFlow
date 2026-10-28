-- Questions a job asks every applicant, some of which can be knockouts, and the answers each application gave.
CREATE TABLE IF NOT EXISTS rec_screening_questions (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    job_id          UUID NOT NULL REFERENCES rec_jobs(id) ON DELETE CASCADE,
    position        INT  NOT NULL,
    question_text   VARCHAR(300) NOT NULL,
    question_type   VARCHAR(10)  NOT NULL CHECK (question_type IN ('YES_NO','NUMBER','TEXT')),
    required        BOOLEAN NOT NULL DEFAULT FALSE,
    expected_answer VARCHAR(3),
    minimum_number  NUMERIC(12,2)
);
CREATE INDEX IF NOT EXISTS idx_rec_screening_questions_job ON rec_screening_questions (job_id, position);

-- The answer keeps the question as it was asked, so changing the questions later does not rewrite what applicants were asked.
CREATE TABLE IF NOT EXISTS rec_screening_answers (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    application_id  UUID NOT NULL REFERENCES rec_applications(id) ON DELETE CASCADE,
    question_id     UUID REFERENCES rec_screening_questions(id) ON DELETE SET NULL,
    position        INT  NOT NULL,
    question_text   VARCHAR(300) NOT NULL,
    question_type   VARCHAR(10)  NOT NULL,
    knockout        BOOLEAN NOT NULL DEFAULT FALSE,
    answer          VARCHAR(1000),
    meets_criteria  BOOLEAN
);
CREATE INDEX IF NOT EXISTS idx_rec_screening_answers_app ON rec_screening_answers (application_id, position);

-- How many knockout answers did not meet the criteria. Advisory: a person decides.
ALTER TABLE rec_applications ADD COLUMN IF NOT EXISTS knockouts_failed INT NOT NULL DEFAULT 0;
