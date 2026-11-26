-- Training Provider 0175: resources a programme version lists as needed.
-- The provider's own list of the kinds and quantities of resources it says a programme needs, each with a note of where the need comes from.
-- It holds no standard and no regulatory figure, and a need being met says nothing about whether the programme meets any standard.
-- Changed only while the programme version is a draft (the service enforces this); withdrawn with a reason, never deleted.

CREATE TABLE trainprov_programme_resource_needs (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    programme_version_id UUID NOT NULL REFERENCES trainprov_programme_versions(id),
    resource_type        VARCHAR(30) NOT NULL,
    description          VARCHAR(200) NOT NULL,
    match_text           VARCHAR(100),
    quantity             INTEGER NOT NULL,
    per_learners         INTEGER,
    source_note          TEXT NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    withdrawn_reason     TEXT,
    withdrawn_by         UUID,
    withdrawn_at         TIMESTAMPTZ,
    created_by           UUID NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_prn_type CHECK (COALESCE(resource_type IN ('EQUIPMENT', 'VEHICLE', 'PRACTICAL_KIT', 'SIMULATOR', 'IT_EQUIPMENT', 'CONSUMABLES', 'OTHER'), FALSE)),
    CONSTRAINT ck_trainprov_prn_description CHECK (COALESCE(char_length(btrim(description)), 0) >= 2),
    CONSTRAINT ck_trainprov_prn_match CHECK (match_text IS NULL OR COALESCE(char_length(btrim(match_text)), 0) >= 2),
    CONSTRAINT ck_trainprov_prn_qty CHECK (quantity >= 1 AND quantity <= 100000),
    CONSTRAINT ck_trainprov_prn_per CHECK (per_learners IS NULL OR (per_learners >= 1 AND per_learners <= 100000)),
    CONSTRAINT ck_trainprov_prn_source CHECK (COALESCE(char_length(btrim(source_note)), 0) >= 10),
    CONSTRAINT ck_trainprov_prn_status CHECK (COALESCE(status IN ('ACTIVE', 'WITHDRAWN'), FALSE)),
    CONSTRAINT ck_trainprov_prn_withdrawn CHECK (
        (status = 'ACTIVE' AND withdrawn_reason IS NULL AND withdrawn_by IS NULL AND withdrawn_at IS NULL)
        OR (status = 'WITHDRAWN' AND COALESCE(char_length(btrim(withdrawn_reason)), 0) >= 10 AND withdrawn_by IS NOT NULL AND withdrawn_at IS NOT NULL))
);
CREATE INDEX idx_trainprov_prn_version ON trainprov_programme_resource_needs (tenant_id, programme_version_id, status);
