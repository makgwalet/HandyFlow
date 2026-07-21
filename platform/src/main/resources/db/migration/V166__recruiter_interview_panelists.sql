-- V__PLACEHOLDER3__recruiter_interview_panelists.sql
-- RENAME: replace __PLACEHOLDER3__ with your real next Flyway version.
-- Independent of the offer-terms and interview-location migrations from
-- earlier this session — no ordering dependency between them, they alter
-- different tables/columns.

CREATE TABLE rec_interview_panelists (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    interview_id  UUID        NOT NULL REFERENCES rec_interviews(id) ON DELETE CASCADE,
    user_id       UUID        NOT NULL REFERENCES users(id),
    user_name     VARCHAR(255),
    created_at    TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_rec_interview_panelists PRIMARY KEY (id),
    CONSTRAINT uq_rec_interview_panelist UNIQUE (interview_id, user_id)
);

CREATE INDEX idx_rec_interview_panelists_interview ON rec_interview_panelists(interview_id);