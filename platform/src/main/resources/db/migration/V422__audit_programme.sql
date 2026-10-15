-- A21: audit programme steps taken from the built-in control library (IT, cyber, operational, compliance). The wording is copied onto the step when it
-- is applied, so later changes to the library never rewrite what an engagement was asked to test.
CREATE TABLE audit_programme_steps (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    engagement_id    UUID NOT NULL,
    control_code     VARCHAR(10) NOT NULL,
    domain           VARCHAR(12) NOT NULL,
    area             VARCHAR(100) NOT NULL,
    objective        TEXT NOT NULL,
    procedure        TEXT NOT NULL,
    reference        VARCHAR(255),
    status           VARCHAR(14) NOT NULL DEFAULT 'NOT_STARTED',   -- NOT_STARTED | IN_PROGRESS | COMPLETED | NOT_APPLICABLE
    conclusion       VARCHAR(20),                                  -- EFFECTIVE | PARTIALLY_EFFECTIVE | INEFFECTIVE (only when COMPLETED)
    note             TEXT,
    finding_id       UUID,
    created_by       UUID NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by       UUID,
    updated_by_name  VARCHAR(255),
    updated_at       TIMESTAMPTZ,
    CONSTRAINT uq_audit_programme_step UNIQUE (engagement_id, control_code),
    CONSTRAINT ck_audit_programme_status CHECK (status IN ('NOT_STARTED','IN_PROGRESS','COMPLETED','NOT_APPLICABLE')),
    CONSTRAINT ck_audit_programme_conclusion CHECK (conclusion IS NULL OR conclusion IN ('EFFECTIVE','PARTIALLY_EFFECTIVE','INEFFECTIVE')),
    CONSTRAINT ck_audit_programme_done CHECK ((status = 'COMPLETED') = (conclusion IS NOT NULL))
);
CREATE INDEX idx_audit_programme_engagement ON audit_programme_steps (tenant_id, engagement_id);
