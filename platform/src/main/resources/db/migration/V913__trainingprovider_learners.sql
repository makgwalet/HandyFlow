-- Training Provider: learners, applications and learner enrolments.
-- These sit beside the existing client / delegate / enrolment model; nothing is migrated or changed there.
-- A learner is a person the provider trains in its own right. A learner enrolment is frozen to the programme
-- version and site the learner started on. Nothing is deleted: learners are archived, applications are
-- declined or withdrawn, enrolments are withdrawn or cancelled, each with a reason. History is never edited.
-- No eligibility, funding or regulatory content is seeded.

CREATE TABLE trainprov_learners (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    learner_no        VARCHAR(30) NOT NULL,
    first_names       VARCHAR(150) NOT NULL,
    surname           VARCHAR(150) NOT NULL,
    id_type           VARCHAR(20) NOT NULL DEFAULT 'SA_ID' CHECK (id_type IN ('SA_ID', 'PASSPORT', 'OTHER', 'NONE')),
    id_number         VARCHAR(40),
    date_of_birth     DATE,
    email             VARCHAR(200),
    phone             VARCHAR(50),
    address           TEXT,
    province          VARCHAR(60),
    client_id         UUID REFERENCES trainprov_clients(id),
    delegate_id       UUID REFERENCES trainprov_delegates(id),
    consent_given     BOOLEAN NOT NULL DEFAULT FALSE,
    consent_on        DATE,
    consent_note      TEXT,
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    archived_reason   TEXT,
    archived_by       UUID,
    archived_at       TIMESTAMPTZ,
    created_by        UUID NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_learner_no UNIQUE (tenant_id, learner_no),
    CONSTRAINT ck_trainprov_learner_id CHECK (
        (id_type = 'NONE' AND id_number IS NULL) OR (id_type <> 'NONE' AND COALESCE(char_length(btrim(id_number)), 0) >= 3)),
    CONSTRAINT ck_trainprov_learner_consent CHECK (
        (consent_given = FALSE AND consent_on IS NULL) OR (consent_given = TRUE AND consent_on IS NOT NULL)),
    CONSTRAINT ck_trainprov_learner_archived CHECK (
        (status = 'ACTIVE' AND archived_reason IS NULL AND archived_by IS NULL AND archived_at IS NULL)
        OR (status = 'ARCHIVED' AND COALESCE(char_length(btrim(archived_reason)), 0) >= 10 AND archived_by IS NOT NULL AND archived_at IS NOT NULL))
);
-- The same identity document cannot belong to two live learners.
CREATE UNIQUE INDEX uq_trainprov_learner_identity ON trainprov_learners (tenant_id, id_type, lower(btrim(id_number)))
    WHERE id_number IS NOT NULL AND status = 'ACTIVE';
CREATE INDEX idx_trainprov_learner_tenant ON trainprov_learners (tenant_id, status, surname);
CREATE INDEX idx_trainprov_learner_client ON trainprov_learners (tenant_id, client_id);

-- A learner's application to a programme. A person records the entry check; the system never decides eligibility.
CREATE TABLE trainprov_learner_applications (
    id                       UUID PRIMARY KEY,
    tenant_id                UUID NOT NULL,
    learner_id               UUID NOT NULL REFERENCES trainprov_learners(id),
    programme_id             UUID NOT NULL REFERENCES trainprov_programmes(id),
    site_id                  UUID REFERENCES trainprov_delivery_sites(id),
    preferred_start          DATE,
    source_note              VARCHAR(300),
    status                   VARCHAR(30) NOT NULL DEFAULT 'RECEIVED'
        CHECK (status IN ('RECEIVED', 'UNDER_REVIEW', 'DOCUMENTS_REQUESTED', 'ACCEPTED', 'DECLINED', 'WITHDRAWN', 'ENROLLED')),
    status_reason            TEXT,
    entry_requirements_met   BOOLEAN,
    entry_check_note         TEXT,
    entry_checked_by         UUID,
    entry_checked_at         TIMESTAMPTZ,
    entry_override_reason    TEXT,
    created_by               UUID NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                  BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_app_accepted CHECK (
        status NOT IN ('ACCEPTED', 'ENROLLED')
        OR (entry_requirements_met IS NOT NULL AND COALESCE(char_length(btrim(entry_check_note)), 0) >= 10 AND entry_checked_by IS NOT NULL
            AND (entry_requirements_met = TRUE OR COALESCE(char_length(btrim(entry_override_reason)), 0) >= 10))),
    CONSTRAINT ck_trainprov_app_reason CHECK (
        status NOT IN ('DECLINED', 'WITHDRAWN', 'DOCUMENTS_REQUESTED') OR COALESCE(char_length(btrim(status_reason)), 0) >= 10)
);
CREATE INDEX idx_trainprov_app_tenant ON trainprov_learner_applications (tenant_id, status);
CREATE INDEX idx_trainprov_app_learner ON trainprov_learner_applications (tenant_id, learner_id);
CREATE INDEX idx_trainprov_app_programme ON trainprov_learner_applications (tenant_id, programme_id);
-- One open application per learner per programme.
CREATE UNIQUE INDEX uq_trainprov_app_open ON trainprov_learner_applications (tenant_id, learner_id, programme_id)
    WHERE status IN ('RECEIVED', 'UNDER_REVIEW', 'DOCUMENTS_REQUESTED', 'ACCEPTED');

CREATE TABLE trainprov_learner_application_events (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    application_id   UUID NOT NULL REFERENCES trainprov_learner_applications(id),
    from_status      VARCHAR(30),
    to_status        VARCHAR(30) NOT NULL,
    reason           TEXT,
    override_reason  TEXT,
    acted_by         UUID NOT NULL,
    acted_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_trainprov_app_events ON trainprov_learner_application_events (tenant_id, application_id, acted_at);

-- A learner's enrolment on a programme, frozen to the version and site they started on.
-- cover_outcome records what the accreditation check said on the day; it is never recalculated.
CREATE TABLE trainprov_learner_enrolments (
    id                         UUID PRIMARY KEY,
    tenant_id                  UUID NOT NULL,
    enrolment_no               VARCHAR(30) NOT NULL,
    learner_id                 UUID NOT NULL REFERENCES trainprov_learners(id),
    programme_id               UUID NOT NULL REFERENCES trainprov_programmes(id),
    programme_version_id       UUID NOT NULL REFERENCES trainprov_programme_versions(id),
    qualification_version_id   UUID REFERENCES trainprov_qualification_versions(id),
    site_id                    UUID NOT NULL REFERENCES trainprov_delivery_sites(id),
    application_id             UUID REFERENCES trainprov_learner_applications(id),
    client_id                  UUID REFERENCES trainprov_clients(id),
    start_date                 DATE NOT NULL,
    expected_end_date          DATE,
    status                     VARCHAR(20) NOT NULL DEFAULT 'ENROLLED'
        CHECK (status IN ('ENROLLED', 'IN_PROGRESS', 'ON_HOLD', 'WITHDRAWN', 'CANCELLED')),
    status_reason              TEXT,
    training_activity          VARCHAR(20) NOT NULL CHECK (training_activity IN ('ACCREDITED', 'NON_ACCREDITED', 'WORKPLACE_BASED')),
    cover_outcome              VARCHAR(40),
    cover_checked_on           DATE,
    cover_override_reason      TEXT,
    created_by                 UUID NOT NULL,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                    BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_lenrol_no UNIQUE (tenant_id, enrolment_no),
    CONSTRAINT ck_trainprov_lenrol_dates CHECK (expected_end_date IS NULL OR expected_end_date >= start_date),
    CONSTRAINT ck_trainprov_lenrol_reason CHECK (
        status NOT IN ('ON_HOLD', 'WITHDRAWN', 'CANCELLED') OR COALESCE(char_length(btrim(status_reason)), 0) >= 10),
    CONSTRAINT ck_trainprov_lenrol_cover CHECK (
        training_activity <> 'ACCREDITED'
        OR (cover_outcome IS NOT NULL AND cover_checked_on IS NOT NULL
            AND (cover_outcome IN ('COVERED', 'COVERED_WITH_CONDITIONS', 'COVERED_RENEWAL_PENDING')
                 OR COALESCE(char_length(btrim(cover_override_reason)), 0) >= 10)))
);
CREATE INDEX idx_trainprov_lenrol_tenant ON trainprov_learner_enrolments (tenant_id, status);
CREATE INDEX idx_trainprov_lenrol_learner ON trainprov_learner_enrolments (tenant_id, learner_id);
CREATE INDEX idx_trainprov_lenrol_programme ON trainprov_learner_enrolments (tenant_id, programme_id, site_id);
-- A learner can have only one live enrolment on a programme.
CREATE UNIQUE INDEX uq_trainprov_lenrol_live ON trainprov_learner_enrolments (tenant_id, learner_id, programme_id)
    WHERE status IN ('ENROLLED', 'IN_PROGRESS', 'ON_HOLD');

CREATE TABLE trainprov_learner_enrolment_events (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    enrolment_id   UUID NOT NULL REFERENCES trainprov_learner_enrolments(id),
    from_status    VARCHAR(20),
    to_status      VARCHAR(20) NOT NULL,
    reason         TEXT,
    acted_by       UUID NOT NULL,
    acted_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_trainprov_lenrol_events ON trainprov_learner_enrolment_events (tenant_id, enrolment_id, acted_at);
