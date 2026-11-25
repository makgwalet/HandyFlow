-- Compliance Services: outside professionals the practice works with. An accreditation someone types in is only a claim until a different
-- person has checked it and recorded how. Nothing is deleted: providers are made inactive and accreditations are retired, each with a reason.

CREATE TABLE IF NOT EXISTS compliance_providers (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    name            VARCHAR(200) NOT NULL,
    kind            VARCHAR(10) NOT NULL DEFAULT 'INDIVIDUAL',
    email           VARCHAR(255),
    phone           VARCHAR(50),
    coverage        VARCHAR(500),
    notes           TEXT,
    status          VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    inactive_reason TEXT,
    created_by      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_compliance_provider_kind CHECK (kind IN ('INDIVIDUAL', 'FIRM')),
    CONSTRAINT ck_compliance_provider_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_compliance_provider_inactive CHECK ((status = 'INACTIVE') = (inactive_reason IS NOT NULL AND length(btrim(inactive_reason)) >= 10))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_provider_email ON compliance_providers(tenant_id, lower(email)) WHERE email IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_compliance_provider_status ON compliance_providers(tenant_id, status);

CREATE TABLE IF NOT EXISTS compliance_provider_services (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    provider_id  UUID NOT NULL REFERENCES compliance_providers(id),
    service_code VARCHAR(60) NOT NULL,
    version      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_compliance_provider_service UNIQUE (provider_id, service_code)
);
CREATE INDEX IF NOT EXISTS idx_compliance_provider_service ON compliance_provider_services(tenant_id, provider_id);

CREATE TABLE IF NOT EXISTS compliance_provider_accreditations (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    provider_id   UUID NOT NULL REFERENCES compliance_providers(id),
    body          VARCHAR(200) NOT NULL,
    reference     VARCHAR(100) NOT NULL,
    issued_on     DATE,
    expires_on    DATE,
    status        VARCHAR(10) NOT NULL DEFAULT 'CLAIMED',
    recorded_by   UUID NOT NULL,
    recorded_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    checked_by    UUID,
    checked_at    TIMESTAMPTZ,
    check_note    TEXT,
    retired_by    UUID,
    retired_at    TIMESTAMPTZ,
    retire_note   TEXT,
    version       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_compliance_provider_accred_status CHECK (status IN ('CLAIMED', 'VERIFIED', 'REJECTED', 'RETIRED')),
    CONSTRAINT ck_compliance_provider_accred_dates CHECK (issued_on IS NULL OR expires_on IS NULL OR expires_on >= issued_on),
    CONSTRAINT ck_compliance_provider_accred_checked CHECK (
        (status IN ('VERIFIED', 'REJECTED')) = (checked_by IS NOT NULL AND checked_at IS NOT NULL AND check_note IS NOT NULL AND length(btrim(check_note)) >= 10)
        OR status = 'RETIRED'),
    CONSTRAINT ck_compliance_provider_accred_other_person CHECK (checked_by IS NULL OR checked_by <> recorded_by),
    CONSTRAINT ck_compliance_provider_accred_retired CHECK ((status = 'RETIRED') = (retired_by IS NOT NULL AND retired_at IS NOT NULL AND retire_note IS NOT NULL AND length(btrim(retire_note)) >= 10))
);
CREATE INDEX IF NOT EXISTS idx_compliance_provider_accred ON compliance_provider_accreditations(tenant_id, provider_id);
