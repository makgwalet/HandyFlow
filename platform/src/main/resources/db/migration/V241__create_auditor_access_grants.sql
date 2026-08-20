-- Placeholder version — confirm real next version before applying.
CREATE TABLE auditor_access_grants (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID NOT NULL,
    portal_user_id         UUID,
    invite_email           VARCHAR(255) NOT NULL,
    status                 VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    invite_token           VARCHAR(255) UNIQUE,
    invite_token_expires_at TIMESTAMPTZ,
    invited_by             UUID,
    invited_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    accepted_at            TIMESTAMPTZ,
    revoked_by             UUID,
    revoked_at             TIMESTAMPTZ
);

CREATE INDEX idx_auditor_grants_tenant ON auditor_access_grants (tenant_id);