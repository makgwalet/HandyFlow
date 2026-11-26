-- Training Provider 0173: practitioner delivery permissions per site.
-- Says which practitioners the provider has recorded as permitted to deliver (facilitate), assess, moderate or assess RPL at which delivery site,
-- optionally for one programme, between two dates. It is the provider's own record of who works where, with the basis for it. It is not a
-- regulatory approval and does not replace the appointment checks on the practitioner (the credential and its verification stay there).
-- Withdrawn with a reason, never deleted. One live permission per practitioner, site, role and programme (or all programmes).

CREATE TABLE trainprov_site_permissions (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    practitioner_id  UUID NOT NULL REFERENCES trainprov_practitioners(id),
    site_id          UUID NOT NULL REFERENCES trainprov_delivery_sites(id),
    role             VARCHAR(20) NOT NULL,
    programme_id     UUID REFERENCES trainprov_programmes(id),
    valid_from       DATE NOT NULL,
    valid_to         DATE,
    basis            TEXT NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    withdrawn_reason TEXT,
    withdrawn_by     UUID,
    withdrawn_at     TIMESTAMPTZ,
    created_by       UUID NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_trainprov_siteperm_role CHECK (COALESCE(role IN ('FACILITATOR', 'ASSESSOR', 'MODERATOR', 'RPL_ASSESSOR'), FALSE)),
    CONSTRAINT ck_trainprov_siteperm_dates CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT ck_trainprov_siteperm_basis CHECK (COALESCE(char_length(btrim(basis)), 0) >= 10),
    CONSTRAINT ck_trainprov_siteperm_status CHECK (
        (status = 'ACTIVE' AND withdrawn_reason IS NULL AND withdrawn_by IS NULL AND withdrawn_at IS NULL)
        OR (status = 'WITHDRAWN' AND COALESCE(char_length(btrim(withdrawn_reason)), 0) >= 10 AND withdrawn_by IS NOT NULL AND withdrawn_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_trainprov_siteperm_live ON trainprov_site_permissions
    (tenant_id, practitioner_id, site_id, role, COALESCE(programme_id, '00000000-0000-0000-0000-000000000000'::uuid)) WHERE status = 'ACTIVE';
CREATE INDEX idx_trainprov_siteperm_site ON trainprov_site_permissions (tenant_id, site_id, status);
CREATE INDEX idx_trainprov_siteperm_person ON trainprov_site_permissions (tenant_id, practitioner_id, status);
