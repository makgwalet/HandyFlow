-- ============================================================================
-- Legal / Compliance — provider submissions (write access for providers, kept to an inbox). Confirm the next free Flyway version before applying (V834 is chunk 30).
--
-- A provider whose client granted the SUBMIT scope can send that client observations, recommendations and requests for information. Rows belong to the CLIENT tenant
-- (tenant_id); provider_tenant_id and provider_client_id say who sent it. Nothing here changes any of the client's registers: the client's staff accept, decline or complete each
-- submission themselves. The scope values live in the existing granted_scopes / requested_scopes text columns (22 characters at most with all three, limit 30), so no change to those.
-- ============================================================================

CREATE TABLE legalcompliance_provider_submissions (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    provider_client_id    UUID NOT NULL REFERENCES legalcompliance_provider_clients (id),
    provider_tenant_id    UUID NOT NULL,
    provider_name         VARCHAR(255) NOT NULL,
    reference             VARCHAR(20) NOT NULL,
    kind                  VARCHAR(20) NOT NULL,
    area                  VARCHAR(12) NOT NULL,
    priority              VARCHAR(6) NOT NULL,
    title                 VARCHAR(200) NOT NULL,
    body                  TEXT NOT NULL,
    respond_by            DATE,
    submitted_by_user_id  UUID NOT NULL,
    submitted_by_name     VARCHAR(255),
    status                VARCHAR(10) NOT NULL DEFAULT 'SUBMITTED',
    response              TEXT,
    responded_by_name     VARCHAR(255),
    responded_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_lc_psub_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_lc_psub_kind CHECK (kind IN ('OBSERVATION','RECOMMENDATION','INFORMATION_REQUEST')),
    CONSTRAINT ck_lc_psub_area CHECK (area IN ('OBLIGATIONS','RISKS','CONTROLS','POLICIES','PRIVACY','INCIDENTS','GOVERNANCE','OTHER')),
    CONSTRAINT ck_lc_psub_priority CHECK (priority IN ('LOW','NORMAL','HIGH')),
    CONSTRAINT ck_lc_psub_status CHECK (status IN ('SUBMITTED','ACCEPTED','DECLINED','COMPLETED','WITHDRAWN')),
    CONSTRAINT ck_lc_psub_title CHECK (length(trim(title)) >= 5),
    CONSTRAINT ck_lc_psub_body CHECK (length(trim(body)) >= 20),
    CONSTRAINT ck_lc_psub_answered CHECK (
        (status IN ('SUBMITTED','WITHDRAWN') AND responded_at IS NULL)
        OR (status = 'ACCEPTED' AND responded_at IS NOT NULL)
        OR (status IN ('DECLINED','COMPLETED') AND responded_at IS NOT NULL AND response IS NOT NULL AND length(trim(response)) >= 10))
);

CREATE INDEX ix_lc_psub_client ON legalcompliance_provider_submissions (tenant_id, status, created_at DESC);
CREATE INDEX ix_lc_psub_provider ON legalcompliance_provider_submissions (provider_tenant_id, provider_client_id, created_at DESC);

-- Sending a submission writes into another organisation's inbox, so the permission is SENSITIVE and ADMIN holds it by default. Responding uses the existing MANAGE and ADMIN authorities.
INSERT INTO permissions (id, name, description)
SELECT gen_random_uuid(), 'LEGALCOMPLIANCE_PROVIDER_SUBMIT', 'Send observations, recommendations and information requests to a client of this provider'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'LEGALCOMPLIANCE_PROVIDER_SUBMIT');

UPDATE permissions SET module_key = 'legalcompliance', resource = 'provider_submission', verb = 'SUBMIT', sensitivity = 'SENSITIVE', requires_second_person = false, grantable_by = 'ADMIN'
WHERE name = 'LEGALCOMPLIANCE_PROVIDER_SUBMIT';

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, np.id FROM role_permissions rp
JOIN permissions op ON op.id = rp.permission_id AND op.name = 'LEGALCOMPLIANCE_ADMIN'
JOIN permissions np ON np.name = 'LEGALCOMPLIANCE_PROVIDER_SUBMIT'
ON CONFLICT DO NOTHING;
