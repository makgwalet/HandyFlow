-- Compliance Services: quality review. A review is a second person checking a piece of work (an assessment, a case, a quote) against a checklist.
-- Checklist wording is copied onto each review, so changing a template never changes a review that has been done. A review that finds problems
-- sends the work back for rework and the next round is a new review; nothing is overwritten and nothing is deleted.

CREATE TABLE IF NOT EXISTS compliance_review_templates (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    name          VARCHAR(200) NOT NULL,
    subject_type  VARCHAR(12) NOT NULL,
    description   TEXT,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_by    UUID,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_compliance_review_template_subject CHECK (subject_type IN ('ASSESSMENT', 'CASE', 'QUOTE', 'OTHER'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_review_template_name ON compliance_review_templates(tenant_id, lower(name));

CREATE TABLE IF NOT EXISTS compliance_review_template_items (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    template_id  UUID NOT NULL REFERENCES compliance_review_templates(id) ON DELETE CASCADE,
    position     INT NOT NULL,
    text         VARCHAR(500) NOT NULL,
    critical     BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_compliance_review_template_item UNIQUE (template_id, position),
    CONSTRAINT ck_compliance_review_template_item_position CHECK (position > 0)
);

CREATE TABLE IF NOT EXISTS compliance_reviews (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    client_id           UUID NOT NULL REFERENCES compliance_service_clients(id),
    subject_type        VARCHAR(12) NOT NULL,
    subject_id          UUID,
    subject_label       VARCHAR(250) NOT NULL,
    template_id         UUID REFERENCES compliance_review_templates(id),
    template_name       VARCHAR(200) NOT NULL,
    round               INT NOT NULL DEFAULT 1,
    previous_review_id  UUID REFERENCES compliance_reviews(id),
    status              VARCHAR(15) NOT NULL DEFAULT 'IN_PROGRESS',
    sample              BOOLEAN NOT NULL DEFAULT FALSE,
    prepared_by         UUID,
    reviewed_by         UUID,
    outcome_note        TEXT,
    exception_reason    TEXT,
    started_by          UUID,
    started_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_compliance_review_subject CHECK (subject_type IN ('ASSESSMENT', 'CASE', 'QUOTE', 'OTHER')),
    CONSTRAINT ck_compliance_review_status CHECK (status IN ('IN_PROGRESS', 'APPROVED', 'REWORK_REQUIRED')),
    CONSTRAINT ck_compliance_review_round CHECK (round >= 1),
    CONSTRAINT ck_compliance_review_done CHECK (status = 'IN_PROGRESS' OR (reviewed_by IS NOT NULL AND completed_at IS NOT NULL AND outcome_note IS NOT NULL)),
    CONSTRAINT ck_compliance_review_open CHECK (status <> 'IN_PROGRESS' OR (reviewed_by IS NULL AND completed_at IS NULL))
);
-- a piece of work has one review in progress at a time, and one review per round
CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_review_open ON compliance_reviews(tenant_id, subject_type, subject_id) WHERE status = 'IN_PROGRESS' AND subject_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_review_round ON compliance_reviews(tenant_id, subject_type, subject_id, round) WHERE subject_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_compliance_review_client ON compliance_reviews(tenant_id, client_id);
CREATE INDEX IF NOT EXISTS idx_compliance_review_status ON compliance_reviews(tenant_id, status);

CREATE TABLE IF NOT EXISTS compliance_review_items (
    id         UUID PRIMARY KEY,
    tenant_id  UUID NOT NULL,
    review_id  UUID NOT NULL REFERENCES compliance_reviews(id) ON DELETE CASCADE,
    position   INT NOT NULL,
    text       VARCHAR(500) NOT NULL,
    critical   BOOLEAN NOT NULL DEFAULT FALSE,
    result     VARCHAR(8) NOT NULL DEFAULT 'PENDING',
    note       TEXT,
    version    BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_compliance_review_item UNIQUE (review_id, position),
    CONSTRAINT ck_compliance_review_item_result CHECK (result IN ('PENDING', 'PASS', 'FAIL', 'NA')),
    CONSTRAINT ck_compliance_review_item_note CHECK (result NOT IN ('FAIL', 'NA') OR note IS NOT NULL)
);
