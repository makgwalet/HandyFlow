-- Training Provider: accreditation foundations.
-- Authorities, delivery sites, provider accreditations (with history) and the
-- scope of each accreditation per programme and per delivery site.
-- No regulatory content is seeded: the provider enters its own authorities,
-- numbers and dates. Nothing is deleted; records are retired or withdrawn with a reason.

CREATE TABLE trainprov_authorities (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    name            VARCHAR(200) NOT NULL,
    authority_type  VARCHAR(30) NOT NULL
        CHECK (authority_type IN ('QCTO', 'SETA', 'UMALUSI', 'CHE', 'PROFESSIONAL_BODY', 'OTHER')),
    notes           TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RETIRED')),
    retired_reason  TEXT,
    retired_by      UUID,
    retired_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_authority_retired CHECK (
        (status = 'ACTIVE' AND retired_reason IS NULL AND retired_by IS NULL AND retired_at IS NULL)
        OR (status = 'RETIRED' AND char_length(btrim(retired_reason)) >= 10 AND retired_by IS NOT NULL AND retired_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_trainprov_authority_name ON trainprov_authorities (tenant_id, lower(name));

CREATE TABLE trainprov_delivery_sites (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    name            VARCHAR(200) NOT NULL,
    site_type       VARCHAR(30) NOT NULL
        CHECK (site_type IN ('CAMPUS', 'DELIVERY_SITE', 'ASSESSMENT_CENTRE', 'OTHER')),
    address         TEXT,
    province        VARCHAR(60),
    contact_person  VARCHAR(200),
    phone           VARCHAR(30),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RETIRED')),
    retired_reason  TEXT,
    retired_by      UUID,
    retired_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_site_retired CHECK (
        (status = 'ACTIVE' AND retired_reason IS NULL AND retired_by IS NULL AND retired_at IS NULL)
        OR (status = 'RETIRED' AND char_length(btrim(retired_reason)) >= 10 AND retired_by IS NOT NULL AND retired_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_trainprov_site_name ON trainprov_delivery_sites (tenant_id, lower(name));

CREATE TABLE trainprov_accreditations (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    authority_id          UUID NOT NULL REFERENCES trainprov_authorities(id),
    title                 VARCHAR(200) NOT NULL,
    accreditation_number  VARCHAR(100),
    status                VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'APPLICATION_PREPARATION', 'SUBMITTED', 'UNDER_REVIEW',
                          'MORE_INFORMATION_REQUIRED', 'CONDITIONAL_APPROVAL', 'APPROVED', 'REJECTED',
                          'EXPIRED', 'SUSPENDED', 'WITHDRAWN', 'RENEWAL_PENDING')),
    submitted_on          DATE,
    approval_date         DATE,
    expiry_date           DATE,
    conditions            TEXT,
    status_reason         TEXT,
    verification_source   VARCHAR(300),
    last_verified_on      DATE,
    verified_by           UUID,
    created_by            UUID NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_accr_approved_has_number CHECK (
        status NOT IN ('APPROVED', 'CONDITIONAL_APPROVAL')
        OR (accreditation_number IS NOT NULL AND char_length(btrim(accreditation_number)) > 0 AND approval_date IS NOT NULL)),
    CONSTRAINT ck_trainprov_accr_conditional_has_conditions CHECK (
        status <> 'CONDITIONAL_APPROVAL' OR char_length(btrim(conditions)) >= 10),
    CONSTRAINT ck_trainprov_accr_dates CHECK (expiry_date IS NULL OR approval_date IS NULL OR expiry_date >= approval_date),
    CONSTRAINT ck_trainprov_accr_verified CHECK (
        (last_verified_on IS NULL AND verified_by IS NULL AND verification_source IS NULL)
        OR (last_verified_on IS NOT NULL AND verified_by IS NOT NULL AND char_length(btrim(verification_source)) >= 5))
);
CREATE UNIQUE INDEX uq_trainprov_accr_number ON trainprov_accreditations (tenant_id, authority_id, lower(accreditation_number))
    WHERE accreditation_number IS NOT NULL AND status NOT IN ('WITHDRAWN', 'REJECTED');
CREATE INDEX idx_trainprov_accr_tenant_status ON trainprov_accreditations (tenant_id, status);

-- Immutable history of every status change (and every details change on a live record).
CREATE TABLE trainprov_accreditation_events (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    accreditation_id UUID NOT NULL REFERENCES trainprov_accreditations(id),
    from_status      VARCHAR(30),
    to_status        VARCHAR(30) NOT NULL,
    reason           TEXT,
    override_reason  TEXT,
    acted_by         UUID NOT NULL,
    acted_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_trainprov_accr_events ON trainprov_accreditation_events (tenant_id, accreditation_id, acted_at);

-- What an accreditation covers: one row per programme per delivery site.
-- Approval at one site never implies approval at another.
CREATE TABLE trainprov_accreditation_scopes (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    accreditation_id  UUID NOT NULL REFERENCES trainprov_accreditations(id),
    site_id           UUID NOT NULL REFERENCES trainprov_delivery_sites(id),
    course_id         UUID REFERENCES trainprov_courses(id),
    programme_title   VARCHAR(250) NOT NULL,
    qualification_ref VARCHAR(100),
    nqf_level         INTEGER CHECK (nqf_level BETWEEN 1 AND 10),
    credits           INTEGER CHECK (credits >= 0),
    valid_from        DATE,
    valid_to          DATE,
    notes             TEXT,
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'WITHDRAWN')),
    withdrawn_reason  TEXT,
    withdrawn_by      UUID,
    withdrawn_at      TIMESTAMPTZ,
    created_by        UUID NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_scope_dates CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from),
    CONSTRAINT ck_trainprov_scope_withdrawn CHECK (
        (status = 'ACTIVE' AND withdrawn_reason IS NULL AND withdrawn_by IS NULL AND withdrawn_at IS NULL)
        OR (status = 'WITHDRAWN' AND char_length(btrim(withdrawn_reason)) >= 10 AND withdrawn_by IS NOT NULL AND withdrawn_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_trainprov_scope_live ON trainprov_accreditation_scopes
    (tenant_id, accreditation_id, site_id, lower(programme_title), COALESCE(lower(qualification_ref), ''))
    WHERE status = 'ACTIVE';
CREATE INDEX idx_trainprov_scope_course ON trainprov_accreditation_scopes (tenant_id, course_id, site_id);
