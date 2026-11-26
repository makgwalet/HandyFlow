-- Training Provider 0171: optional programme-level workplace hours.
-- Not every programme has workplace hours and none are assumed. A programme version may record a number of workplace hours only together with
-- where that number comes from: the kind of source, its reference, the section or page, the day it takes effect, and who checked it and when.
-- Hours are recorded as the source states them; this holds no default hours and no pass mark, and logged hours are never compared to a pass.
-- Changed only while the programme version is a draft (the service enforces this); withdrawn with a reason, never deleted.

CREATE TABLE trainprov_programme_workplace_hours (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    programme_version_id UUID NOT NULL REFERENCES trainprov_programme_versions(id),
    hours               NUMERIC(8,2) NOT NULL,
    covers              VARCHAR(300),
    source_type         VARCHAR(30) NOT NULL,
    source_reference    VARCHAR(300) NOT NULL,
    source_version_date DATE,
    source_section      VARCHAR(100) NOT NULL,
    effective_date      DATE NOT NULL,
    verified_by         VARCHAR(200) NOT NULL,
    verified_on         DATE NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'WITHDRAWN')),
    withdrawn_reason    TEXT,
    withdrawn_by        UUID,
    withdrawn_at        TIMESTAMPTZ,
    created_by          UUID NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by          UUID NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_pwh_hours CHECK (hours > 0 AND hours <= 20000),
    CONSTRAINT ck_trainprov_pwh_source_type CHECK (COALESCE(source_type IN
        ('QUALIFICATION_DOCUMENT', 'CURRICULUM', 'AUTHORITY_LETTER', 'ACCREDITATION_DOCUMENT', 'FUNDER_AGREEMENT', 'PROVIDER_POLICY', 'OTHER'), FALSE)),
    CONSTRAINT ck_trainprov_pwh_reference CHECK (COALESCE(char_length(btrim(source_reference)), 0) >= 3),
    CONSTRAINT ck_trainprov_pwh_section CHECK (COALESCE(char_length(btrim(source_section)), 0) >= 1),
    CONSTRAINT ck_trainprov_pwh_verifier CHECK (COALESCE(char_length(btrim(verified_by)), 0) >= 2),
    CONSTRAINT ck_trainprov_pwh_dates CHECK (source_version_date IS NULL OR source_version_date <= verified_on),
    CONSTRAINT ck_trainprov_pwh_withdrawn CHECK (
        (status = 'ACTIVE' AND withdrawn_reason IS NULL AND withdrawn_by IS NULL AND withdrawn_at IS NULL)
        OR (status = 'WITHDRAWN' AND COALESCE(char_length(btrim(withdrawn_reason)), 0) >= 10 AND withdrawn_by IS NOT NULL AND withdrawn_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_trainprov_pwh_live ON trainprov_programme_workplace_hours (tenant_id, programme_version_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_trainprov_pwh_version ON trainprov_programme_workplace_hours (tenant_id, programme_version_id);
