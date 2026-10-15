-- A19: engagement quality review by someone independent of the engagement team, against a methodology checklist.
CREATE TABLE audit_quality_reviews (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    engagement_id   UUID NOT NULL,
    reviewer_id     UUID NOT NULL,
    reviewer_name   VARCHAR(255) NOT NULL,
    requested_by    UUID NOT NULL,
    requested_by_name VARCHAR(255) NOT NULL,
    requested_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    status          VARCHAR(12) NOT NULL DEFAULT 'REQUESTED',       -- REQUESTED | IN_PROGRESS | COMPLETED | CANCELLED
    conclusion      VARCHAR(20),                                    -- SATISFACTORY | NEEDS_IMPROVEMENT | UNSATISFACTORY
    conclusion_note TEXT,
    completed_at    TIMESTAMPTZ,
    cancel_reason   TEXT,
    cancelled_at    TIMESTAMPTZ,
    CONSTRAINT ck_audit_qr_status CHECK (status IN ('REQUESTED','IN_PROGRESS','COMPLETED','CANCELLED')),
    CONSTRAINT ck_audit_qr_conclusion CHECK (conclusion IS NULL OR conclusion IN ('SATISFACTORY','NEEDS_IMPROVEMENT','UNSATISFACTORY'))
);
CREATE INDEX idx_audit_qr_engagement ON audit_quality_reviews (tenant_id, engagement_id);
-- One live review per engagement at a time.
CREATE UNIQUE INDEX uq_audit_qr_live ON audit_quality_reviews (tenant_id, engagement_id) WHERE status IN ('REQUESTED','IN_PROGRESS');

CREATE TABLE audit_quality_checks (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    review_id     UUID NOT NULL REFERENCES audit_quality_reviews(id) ON DELETE CASCADE,
    item_code     VARCHAR(10) NOT NULL,
    result        VARCHAR(3) NOT NULL,                              -- YES | NO | NA
    note          TEXT,
    answered_by   UUID NOT NULL,
    answered_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_audit_qc UNIQUE (review_id, item_code),
    CONSTRAINT ck_audit_qc_result CHECK (result IN ('YES','NO','NA'))
);
