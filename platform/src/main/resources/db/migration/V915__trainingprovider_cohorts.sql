-- Training provider: venues, cohorts, cohort members and cohort facilitators.
-- A cohort is frozen to the approved programme version it was opened on. Members are learner enrolments on that same version and site.
-- Nothing is deleted: members and facilitators are removed with a reason, cohorts are cancelled with a reason, venues are made inactive with a reason.

CREATE TABLE trainprov_venues (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    site_id         UUID NOT NULL REFERENCES trainprov_delivery_sites(id),
    name            VARCHAR(150) NOT NULL,
    capacity        INTEGER,
    notes           TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    inactive_reason TEXT,
    created_by      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_venue_name UNIQUE (tenant_id, site_id, name),
    CONSTRAINT ck_trainprov_venue_name CHECK (COALESCE(char_length(btrim(name)), 0) >= 2),
    CONSTRAINT ck_trainprov_venue_capacity CHECK (capacity IS NULL OR capacity >= 1),
    CONSTRAINT ck_trainprov_venue_inactive CHECK (
        (status = 'ACTIVE' AND inactive_reason IS NULL)
        OR (status = 'INACTIVE' AND COALESCE(char_length(btrim(inactive_reason)), 0) >= 10))
);
CREATE INDEX idx_trainprov_venue_site ON trainprov_venues (tenant_id, site_id, status);

CREATE TABLE trainprov_cohorts (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    cohort_no            VARCHAR(30) NOT NULL,
    name                 VARCHAR(150) NOT NULL,
    programme_id         UUID NOT NULL REFERENCES trainprov_programmes(id),
    programme_version_id UUID NOT NULL REFERENCES trainprov_programme_versions(id),
    site_id              UUID NOT NULL REFERENCES trainprov_delivery_sites(id),
    start_date           DATE NOT NULL,
    end_date             DATE,
    capacity             INTEGER,
    notes                TEXT,
    status               VARCHAR(20) NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED', 'RUNNING', 'COMPLETED', 'CANCELLED')),
    status_reason        TEXT,
    created_by           UUID NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainprov_cohort_no UNIQUE (tenant_id, cohort_no),
    CONSTRAINT ck_trainprov_cohort_name CHECK (COALESCE(char_length(btrim(name)), 0) >= 2),
    CONSTRAINT ck_trainprov_cohort_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_trainprov_cohort_capacity CHECK (capacity IS NULL OR capacity >= 1),
    CONSTRAINT ck_trainprov_cohort_reason CHECK (status <> 'CANCELLED' OR COALESCE(char_length(btrim(status_reason)), 0) >= 10)
);
CREATE INDEX idx_trainprov_cohort_programme ON trainprov_cohorts (tenant_id, programme_id, status);
CREATE INDEX idx_trainprov_cohort_site ON trainprov_cohorts (tenant_id, site_id, start_date);

CREATE TABLE trainprov_cohort_members (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    cohort_id      UUID NOT NULL REFERENCES trainprov_cohorts(id),
    enrolment_id   UUID NOT NULL REFERENCES trainprov_learner_enrolments(id),
    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REMOVED')),
    removed_reason TEXT,
    removed_by     UUID,
    removed_at     TIMESTAMPTZ,
    added_by       UUID NOT NULL,
    added_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_member_removed CHECK (
        (status = 'ACTIVE' AND removed_reason IS NULL AND removed_by IS NULL AND removed_at IS NULL)
        OR (status = 'REMOVED' AND COALESCE(char_length(btrim(removed_reason)), 0) >= 10 AND removed_by IS NOT NULL AND removed_at IS NOT NULL))
);
-- An enrolment sits in at most one live cohort at a time.
CREATE UNIQUE INDEX uq_trainprov_member_live ON trainprov_cohort_members (tenant_id, enrolment_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_trainprov_member_cohort ON trainprov_cohort_members (tenant_id, cohort_id, status);

CREATE TABLE trainprov_cohort_facilitators (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    cohort_id        UUID NOT NULL REFERENCES trainprov_cohorts(id),
    practitioner_id  UUID NOT NULL REFERENCES trainprov_practitioners(id),
    appointment_id   UUID NOT NULL REFERENCES trainprov_practitioner_appointments(id),
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REMOVED')),
    removed_reason   TEXT,
    removed_by       UUID,
    removed_at       TIMESTAMPTZ,
    added_by         UUID NOT NULL,
    added_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_cfac_removed CHECK (
        (status = 'ACTIVE' AND removed_reason IS NULL AND removed_by IS NULL AND removed_at IS NULL)
        OR (status = 'REMOVED' AND COALESCE(char_length(btrim(removed_reason)), 0) >= 10 AND removed_by IS NOT NULL AND removed_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_trainprov_cfac_live ON trainprov_cohort_facilitators (tenant_id, cohort_id, practitioner_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_trainprov_cfac_cohort ON trainprov_cohort_facilitators (tenant_id, cohort_id, status);

CREATE TABLE trainprov_cohort_events (
    id         UUID PRIMARY KEY,
    tenant_id  UUID NOT NULL,
    cohort_id  UUID NOT NULL REFERENCES trainprov_cohorts(id),
    action     VARCHAR(30) NOT NULL,
    note       TEXT,
    acted_by   UUID NOT NULL,
    acted_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_trainprov_cohort_event ON trainprov_cohort_events (tenant_id, cohort_id, acted_at);
