-- A18: engagement reports as documents (built from a template), a factual-accuracy review by named reviewers,
-- and controlled distribution to named recipients. Sign-off still runs through the existing approval.
CREATE TABLE audit_reports (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    engagement_id           UUID NOT NULL,
    version                 INT  NOT NULL,
    template                VARCHAR(40) NOT NULL,
    title                   VARCHAR(255) NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'DRAFT',   -- DRAFT | FACTUAL_REVIEW | READY | ISSUED | SUPERSEDED
    content_hash            VARCHAR(64),
    created_by              UUID NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    fact_review_started_at  TIMESTAMPTZ,
    fact_review_closed_at   TIMESTAMPTZ,
    issued_by               UUID,
    issued_at               TIMESTAMPTZ,
    CONSTRAINT uq_audit_report_version UNIQUE (tenant_id, engagement_id, version),
    CONSTRAINT ck_audit_report_status CHECK (status IN ('DRAFT','FACTUAL_REVIEW','READY','ISSUED','SUPERSEDED'))
);
CREATE INDEX idx_audit_reports_engagement ON audit_reports (tenant_id, engagement_id);

CREATE TABLE audit_report_sections (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    report_id     UUID NOT NULL REFERENCES audit_reports(id) ON DELETE CASCADE,
    position      INT  NOT NULL,
    title         VARCHAR(255) NOT NULL,
    body          TEXT NOT NULL DEFAULT '',
    auto_findings BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_audit_report_section UNIQUE (report_id, position)
);

CREATE TABLE audit_report_fact_items (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    report_id          UUID NOT NULL REFERENCES audit_reports(id) ON DELETE CASCADE,
    section_position   INT,
    comment            TEXT NOT NULL,
    raised_by          UUID NOT NULL,
    raised_by_name     VARCHAR(255) NOT NULL,
    raised_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    status             VARCHAR(10) NOT NULL DEFAULT 'OPEN',   -- OPEN | ACCEPTED | DECLINED
    response           TEXT,
    responded_by       UUID,
    responded_by_name  VARCHAR(255),
    responded_at       TIMESTAMPTZ,
    CONSTRAINT ck_audit_fact_status CHECK (status IN ('OPEN','ACCEPTED','DECLINED'))
);
CREATE INDEX idx_audit_fact_items_report ON audit_report_fact_items (report_id);
CREATE INDEX idx_audit_fact_items_open ON audit_report_fact_items (tenant_id, raised_at) WHERE status = 'OPEN';

CREATE TABLE audit_report_people (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    report_id       UUID NOT NULL REFERENCES audit_reports(id) ON DELETE CASCADE,
    kind            VARCHAR(10) NOT NULL,                       -- REVIEWER | RECIPIENT
    user_id         UUID NOT NULL,
    user_name       VARCHAR(255) NOT NULL,
    added_by        UUID NOT NULL,
    added_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    first_viewed_at TIMESTAMPTZ,
    last_viewed_at  TIMESTAMPTZ,
    view_count      INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_audit_report_person UNIQUE (report_id, kind, user_id),
    CONSTRAINT ck_audit_person_kind CHECK (kind IN ('REVIEWER','RECIPIENT'))
);
CREATE INDEX idx_audit_report_people_user ON audit_report_people (tenant_id, user_id);
