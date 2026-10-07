-- V334__security_guard_reviews.sql
--
-- Supervisor review of a guard over a period: overall assessment, strengths, areas to improve, training needs,
-- actions agreed and a follow-up date. Saving a review also records a SUPERVISOR rating with the same six scores
-- (rating_id), so the review feeds the operational score through the existing ratings component.
-- A review is a record of what the supervisor said on a date: it is never edited or deleted.

CREATE TABLE security_guard_reviews (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    guard_id          UUID NOT NULL REFERENCES security_guards(id),
    site_id           UUID,
    review_date       DATE NOT NULL,
    period_from       DATE NOT NULL,
    period_to         DATE NOT NULL,
    reviewer_name     VARCHAR(200) NOT NULL,
    overall           VARCHAR(30) NOT NULL CHECK (overall IN ('EXCEEDS', 'MEETS', 'BELOW')),
    punctuality       SMALLINT NOT NULL CHECK (punctuality BETWEEN 1 AND 5),
    professionalism   SMALLINT NOT NULL CHECK (professionalism BETWEEN 1 AND 5),
    appearance        SMALLINT NOT NULL CHECK (appearance BETWEEN 1 AND 5),
    communication     SMALLINT NOT NULL CHECK (communication BETWEEN 1 AND 5),
    alertness         SMALLINT NOT NULL CHECK (alertness BETWEEN 1 AND 5),
    incident_handling SMALLINT NOT NULL CHECK (incident_handling BETWEEN 1 AND 5),
    strengths         TEXT,
    improvements      TEXT,
    training_needs    TEXT,
    actions_agreed    TEXT,
    follow_up_date    DATE,
    rating_id         UUID,
    created_by        UUID,
    created_by_name   VARCHAR(200),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_guard_review_period CHECK (period_to >= period_from)
);

CREATE INDEX idx_security_guard_reviews_guard ON security_guard_reviews (tenant_id, guard_id, review_date DESC);

COMMENT ON TABLE security_guard_reviews IS
    'Supervisor reviews of guards. Immutable. Each one also writes a SUPERVISOR row in security_guard_ratings (rating_id).';
