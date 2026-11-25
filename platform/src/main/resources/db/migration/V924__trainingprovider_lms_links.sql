-- Training provider: links to an outside learning system. A programme version can have one active launch link. The link is a plain web address.
-- No password, key or token is held here. A link is ended with a reason, never deleted. Nothing is synced: progress is still recorded by staff.

CREATE TABLE trainprov_lms_links (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    programme_version_id UUID NOT NULL REFERENCES trainprov_programme_versions(id),
    system_name          VARCHAR(120) NOT NULL,
    launch_url           VARCHAR(1000) NOT NULL,
    notes                TEXT,
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ENDED')),
    ended_reason         TEXT,
    ended_by             UUID,
    ended_at             TIMESTAMPTZ,
    created_by           UUID NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_lms_name CHECK (COALESCE(char_length(btrim(system_name)), 0) >= 2),
    CONSTRAINT ck_trainprov_lms_url CHECK (launch_url ~* '^https://[^ @]+$'),
    CONSTRAINT ck_trainprov_lms_secret CHECK (launch_url !~* '[?&;](token|password|passwd|pwd|secret|key|apikey|api_key|access_token|auth)='),
    CONSTRAINT ck_trainprov_lms_status CHECK (
        (status = 'ACTIVE' AND ended_reason IS NULL AND ended_by IS NULL AND ended_at IS NULL)
        OR (status = 'ENDED' AND COALESCE(char_length(btrim(ended_reason)), 0) >= 10 AND ended_by IS NOT NULL AND ended_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_trainprov_lms_active ON trainprov_lms_links (tenant_id, programme_version_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_trainprov_lms_version ON trainprov_lms_links (tenant_id, programme_version_id);
