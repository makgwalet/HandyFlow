-- Training Provider: qualification and programme catalogue.
-- A qualification is the official reference (what the authority registers).
-- A programme is what the provider offers, built from versioned definitions.
-- A programme version is locked once approved, so a learner's record keeps the
-- version they studied under. Nothing is deleted; things are retired or withdrawn
-- with a reason. No qualification, credit or registration data is seeded.

CREATE TABLE trainprov_qualifications (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    title               VARCHAR(300) NOT NULL,
    qualification_type  VARCHAR(30) NOT NULL
        CHECK (qualification_type IN ('QUALIFICATION', 'PART_QUALIFICATION', 'SKILLS_PROGRAMME', 'UNIT_STANDARD',
                                      'PROFESSIONAL_DEVELOPMENT', 'OTHER')),
    official_ref        VARCHAR(100),
    saqa_id             VARCHAR(50),
    sub_framework       VARCHAR(100),
    nqf_level           INTEGER CHECK (nqf_level BETWEEN 1 AND 10),
    credits             INTEGER CHECK (credits >= 0),
    authority_id        UUID REFERENCES trainprov_authorities(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RETIRED')),
    retired_reason      TEXT,
    retired_by          UUID,
    retired_at          TIMESTAMPTZ,
    created_by          UUID NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_qual_retired CHECK (
        (status = 'ACTIVE' AND retired_reason IS NULL AND retired_by IS NULL AND retired_at IS NULL)
        OR (status = 'RETIRED' AND char_length(btrim(retired_reason)) >= 10 AND retired_by IS NOT NULL AND retired_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_trainprov_qual_ref ON trainprov_qualifications (tenant_id, lower(official_ref))
    WHERE official_ref IS NOT NULL AND status = 'ACTIVE';
CREATE INDEX idx_trainprov_qual_tenant ON trainprov_qualifications (tenant_id, status);

CREATE TABLE trainprov_qualification_versions (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    qualification_id      UUID NOT NULL REFERENCES trainprov_qualifications(id),
    version_no            INTEGER NOT NULL CHECK (version_no >= 1),
    status                VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'CURRENT', 'SUPERSEDED')),
    registration_status   VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN'
        CHECK (registration_status IN ('REGISTERED', 'RE_REGISTERED', 'NOT_YET_REGISTERED', 'LAPSED', 'UNKNOWN')),
    registration_start    DATE,
    registration_end      DATE,
    entry_requirements    TEXT,
    exit_outcomes         TEXT,
    rules_of_combination  TEXT,
    source_note           VARCHAR(300),
    effective_from        DATE,
    created_by            UUID NOT NULL,
    made_current_by       UUID,
    made_current_at       TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    row_version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_qualver UNIQUE (qualification_id, version_no),
    CONSTRAINT ck_trainprov_qualver_dates CHECK (registration_end IS NULL OR registration_start IS NULL OR registration_end >= registration_start),
    CONSTRAINT ck_trainprov_qualver_current CHECK (
        status = 'DRAFT' OR (char_length(btrim(source_note)) >= 5 AND made_current_by IS NOT NULL AND made_current_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_trainprov_qualver_one_current ON trainprov_qualification_versions (qualification_id) WHERE status = 'CURRENT';

CREATE TABLE trainprov_programmes (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    code              VARCHAR(40) NOT NULL,
    title             VARCHAR(300) NOT NULL,
    programme_type    VARCHAR(30) NOT NULL
        CHECK (programme_type IN ('QUALIFICATION', 'PART_QUALIFICATION', 'SKILLS_PROGRAMME', 'UNIT_STANDARD_PROGRAMME',
                                  'NON_ACCREDITED_COURSE', 'PROFESSIONAL_DEVELOPMENT', 'CUSTOM_CORPORATE')),
    training_activity VARCHAR(20) NOT NULL CHECK (training_activity IN ('ACCREDITED', 'NON_ACCREDITED', 'WORKPLACE_BASED')),
    delivery_mode     VARCHAR(20) NOT NULL DEFAULT 'CLASSROOM'
        CHECK (delivery_mode IN ('CLASSROOM', 'PRACTICAL', 'ONLINE', 'BLENDED', 'WORKPLACE')),
    qualification_id  UUID REFERENCES trainprov_qualifications(id),
    course_id         UUID REFERENCES trainprov_courses(id),
    description       TEXT,
    status            VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    retired_reason    TEXT,
    retired_by        UUID,
    retired_at        TIMESTAMPTZ,
    created_by        UUID NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_prog_accredited_has_qual CHECK (training_activity <> 'ACCREDITED' OR qualification_id IS NOT NULL),
    CONSTRAINT ck_trainprov_prog_retired CHECK (
        (status <> 'RETIRED' AND retired_reason IS NULL AND retired_by IS NULL AND retired_at IS NULL)
        OR (status = 'RETIRED' AND char_length(btrim(retired_reason)) >= 10 AND retired_by IS NOT NULL AND retired_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_trainprov_prog_code ON trainprov_programmes (tenant_id, lower(code));
CREATE INDEX idx_trainprov_prog_tenant ON trainprov_programmes (tenant_id, status);

CREATE TABLE trainprov_programme_versions (
    id                         UUID PRIMARY KEY,
    tenant_id                  UUID NOT NULL,
    programme_id               UUID NOT NULL REFERENCES trainprov_programmes(id),
    version_no                 INTEGER NOT NULL CHECK (version_no >= 1),
    status                     VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'APPROVED', 'SUPERSEDED')),
    qualification_version_id   UUID REFERENCES trainprov_qualification_versions(id),
    learning_hours             NUMERIC(8,1) CHECK (learning_hours >= 0),
    credits                    INTEGER CHECK (credits >= 0),
    summary                    TEXT,
    effective_from             DATE,
    created_by                 UUID NOT NULL,
    approved_by                UUID,
    approved_at                TIMESTAMPTZ,
    approval_override_reason   TEXT,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    row_version                BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_progver UNIQUE (programme_id, version_no),
    CONSTRAINT ck_trainprov_progver_approved CHECK (status = 'DRAFT' OR (approved_by IS NOT NULL AND approved_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_trainprov_progver_one_approved ON trainprov_programme_versions (programme_id) WHERE status = 'APPROVED';

CREATE TABLE trainprov_programme_modules (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    programme_version_id UUID NOT NULL REFERENCES trainprov_programme_versions(id),
    seq                  INTEGER NOT NULL CHECK (seq >= 1),
    title                VARCHAR(300) NOT NULL,
    module_type          VARCHAR(20) NOT NULL CHECK (module_type IN ('KNOWLEDGE', 'PRACTICAL', 'WORKPLACE')),
    credits              INTEGER CHECK (credits >= 0),
    notional_hours       NUMERIC(8,1) CHECK (notional_hours >= 0),
    is_required          BOOLEAN NOT NULL DEFAULT TRUE,
    learning_outcomes    TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_trainprov_module_seq UNIQUE (programme_version_id, seq)
);

-- Where a programme is offered. Accredited programmes can only be offered at a site
-- that a current approval covers; that is checked when the offering is made.
CREATE TABLE trainprov_programme_offerings (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    programme_id      UUID NOT NULL REFERENCES trainprov_programmes(id),
    site_id           UUID NOT NULL REFERENCES trainprov_delivery_sites(id),
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'WITHDRAWN')),
    offered_from      DATE NOT NULL,
    coverage_note     VARCHAR(300),
    withdrawn_reason  TEXT,
    withdrawn_by      UUID,
    withdrawn_at      TIMESTAMPTZ,
    created_by        UUID NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_offering_withdrawn CHECK (
        (status = 'ACTIVE' AND withdrawn_reason IS NULL AND withdrawn_by IS NULL AND withdrawn_at IS NULL)
        OR (status = 'WITHDRAWN' AND char_length(btrim(withdrawn_reason)) >= 10 AND withdrawn_by IS NOT NULL AND withdrawn_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_trainprov_offering_live ON trainprov_programme_offerings (programme_id, site_id) WHERE status = 'ACTIVE';

-- An approval scope can now name the programme it covers (the course link stays for older records).
ALTER TABLE trainprov_accreditation_scopes ADD COLUMN programme_id UUID REFERENCES trainprov_programmes(id);
CREATE INDEX idx_trainprov_scope_programme ON trainprov_accreditation_scopes (tenant_id, programme_id, site_id);
