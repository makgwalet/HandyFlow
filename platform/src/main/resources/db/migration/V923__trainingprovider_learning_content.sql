-- Training provider: learning content and progress. A lesson belongs to a programme version and optionally to one of its modules. It is a reading,
-- a link or a document link. A draft can be edited; approval is by a second person or by the author with a written reason; an approved lesson is
-- never edited, it is retired with a reason and replaced. Progress records that a learner finished a lesson. It is not a competency decision,
-- not attendance, and is never used to grade anyone. A progress record is reversed with a reason, never deleted.

CREATE TABLE trainprov_lessons (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    lesson_no            VARCHAR(30) NOT NULL,
    programme_version_id UUID NOT NULL REFERENCES trainprov_programme_versions(id),
    module_id            UUID REFERENCES trainprov_programme_modules(id),
    title                VARCHAR(200) NOT NULL,
    kind                 VARCHAR(20) NOT NULL CHECK (kind IN ('READING', 'LINK', 'DOCUMENT')),
    content_text         TEXT,
    url                  VARCHAR(1000),
    position_no          INTEGER NOT NULL DEFAULT 1 CHECK (position_no >= 1),
    status               VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'APPROVED', 'RETIRED')),
    approved_by          UUID,
    approved_at          TIMESTAMPTZ,
    approval_override_reason TEXT,
    retired_reason       TEXT,
    retired_by           UUID,
    retired_at           TIMESTAMPTZ,
    created_by           UUID NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_lesson_no UNIQUE (tenant_id, lesson_no),
    CONSTRAINT ck_trainprov_lesson_title CHECK (COALESCE(char_length(btrim(title)), 0) >= 2),
    CONSTRAINT ck_trainprov_lesson_content CHECK (
        (kind = 'READING' AND COALESCE(char_length(btrim(content_text)), 0) >= 20 AND url IS NULL)
        OR (kind IN ('LINK', 'DOCUMENT') AND url ~* '^https?://[^ ]+$')),
    CONSTRAINT ck_trainprov_lesson_status CHECK (
        (status = 'DRAFT' AND approved_by IS NULL AND approved_at IS NULL AND retired_reason IS NULL AND retired_by IS NULL AND retired_at IS NULL)
        OR (status = 'APPROVED' AND approved_by IS NOT NULL AND approved_at IS NOT NULL AND retired_reason IS NULL AND retired_by IS NULL AND retired_at IS NULL)
        OR (status = 'RETIRED' AND COALESCE(char_length(btrim(retired_reason)), 0) >= 10 AND retired_by IS NOT NULL AND retired_at IS NOT NULL)),
    CONSTRAINT ck_trainprov_lesson_override CHECK (approval_override_reason IS NULL OR COALESCE(char_length(btrim(approval_override_reason)), 0) >= 10)
);
CREATE UNIQUE INDEX uq_trainprov_lesson_title ON trainprov_lessons (tenant_id, programme_version_id, lower(title)) WHERE status <> 'RETIRED';
CREATE INDEX idx_trainprov_lesson_version ON trainprov_lessons (tenant_id, programme_version_id, status, position_no);

CREATE TABLE trainprov_lesson_progress (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    enrolment_id     UUID NOT NULL REFERENCES trainprov_learner_enrolments(id),
    lesson_id        UUID NOT NULL REFERENCES trainprov_lessons(id),
    finished_on      DATE NOT NULL,
    note             TEXT,
    status           VARCHAR(20) NOT NULL DEFAULT 'FINISHED' CHECK (status IN ('FINISHED', 'REVERSED')),
    reversed_reason  TEXT,
    reversed_by      UUID,
    reversed_at      TIMESTAMPTZ,
    recorded_by      UUID NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_progress_reverse CHECK (
        (status = 'FINISHED' AND reversed_reason IS NULL AND reversed_by IS NULL AND reversed_at IS NULL)
        OR (status = 'REVERSED' AND COALESCE(char_length(btrim(reversed_reason)), 0) >= 10 AND reversed_by IS NOT NULL AND reversed_at IS NOT NULL))
);
-- One standing finish per lesson per enrolment. A reversed one stays on record and the lesson can be finished again.
CREATE UNIQUE INDEX uq_trainprov_progress_live ON trainprov_lesson_progress (tenant_id, enrolment_id, lesson_id) WHERE status = 'FINISHED';
CREATE INDEX idx_trainprov_progress_enrolment ON trainprov_lesson_progress (tenant_id, enrolment_id);
