-- V__PLACEHOLDER4__recruiter_interview_rounds.sql
-- RENAME: replace __PLACEHOLDER4__ with your real next Flyway version.
--
-- WHY per-job, not a tenant-wide reusable library? Keeps v1 simple —
-- "clone rounds from another job" is a reasonable future convenience, not
-- a requirement today. round_template_id on rec_interviews is nullable
-- deliberately: an ad-hoc interview not tied to any defined round must
-- still be schedulable (real hiring processes don't always follow the
-- plan exactly).

CREATE TABLE rec_job_interview_rounds (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id    UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    job_id       UUID        NOT NULL REFERENCES rec_jobs(id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    sequence     INT         NOT NULL,
    description  TEXT,
    created_at   TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_rec_job_interview_rounds PRIMARY KEY (id),
    CONSTRAINT uq_rec_job_round_sequence UNIQUE (job_id, sequence)
);

CREATE INDEX idx_rec_job_interview_rounds_job ON rec_job_interview_rounds(job_id);

ALTER TABLE rec_interviews
    ADD COLUMN round_template_id UUID REFERENCES rec_job_interview_rounds(id);