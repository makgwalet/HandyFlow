-- Training provider: assessment instruments. An instrument belongs to a programme version and optionally to one of its modules.
-- It may carry a mark scale (the maximum mark) but never a pass mark: the mark and the competency decision are separate things and the
-- decision is recorded by an assessor, later, with evidence. An approved instrument is never edited; it is retired and replaced.

CREATE TABLE trainprov_assessment_instruments (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    instrument_no        VARCHAR(30) NOT NULL,
    programme_version_id UUID NOT NULL REFERENCES trainprov_programme_versions(id),
    module_id            UUID REFERENCES trainprov_programme_modules(id),
    title                VARCHAR(200) NOT NULL,
    assessment_type      VARCHAR(20) NOT NULL CHECK (assessment_type IN ('KNOWLEDGE', 'PRACTICAL', 'WORKPLACE', 'INTEGRATED', 'PORTFOLIO')),
    max_mark             NUMERIC(8, 2),
    guidance             TEXT,
    requires_moderation  BOOLEAN NOT NULL DEFAULT TRUE,
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
    CONSTRAINT uq_trainprov_instrument_no UNIQUE (tenant_id, instrument_no),
    CONSTRAINT ck_trainprov_instrument_title CHECK (COALESCE(char_length(btrim(title)), 0) >= 2),
    CONSTRAINT ck_trainprov_instrument_mark CHECK (max_mark IS NULL OR max_mark > 0),
    CONSTRAINT ck_trainprov_instrument_status CHECK (
        (status = 'DRAFT' AND approved_by IS NULL AND approved_at IS NULL AND retired_reason IS NULL AND retired_by IS NULL AND retired_at IS NULL)
        OR (status = 'APPROVED' AND approved_by IS NOT NULL AND approved_at IS NOT NULL AND retired_reason IS NULL AND retired_by IS NULL AND retired_at IS NULL)
        OR (status = 'RETIRED' AND COALESCE(char_length(btrim(retired_reason)), 0) >= 10 AND retired_by IS NOT NULL AND retired_at IS NOT NULL)),
    CONSTRAINT ck_trainprov_instrument_override CHECK (approval_override_reason IS NULL OR COALESCE(char_length(btrim(approval_override_reason)), 0) >= 10)
);
-- One live instrument of a given title per programme version.
CREATE UNIQUE INDEX uq_trainprov_instrument_title ON trainprov_assessment_instruments (tenant_id, programme_version_id, lower(title)) WHERE status <> 'RETIRED';
CREATE INDEX idx_trainprov_instrument_version ON trainprov_assessment_instruments (tenant_id, programme_version_id, status);
CREATE INDEX idx_trainprov_instrument_module ON trainprov_assessment_instruments (tenant_id, module_id);

CREATE TABLE trainprov_assessment_instrument_events (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    instrument_id  UUID NOT NULL REFERENCES trainprov_assessment_instruments(id),
    action         VARCHAR(20) NOT NULL CHECK (action IN ('CREATED', 'CHANGED', 'APPROVED', 'RETIRED')),
    note           TEXT,
    acted_by       UUID NOT NULL,
    acted_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_trainprov_instrument_event ON trainprov_assessment_instrument_events (tenant_id, instrument_id, acted_at);
