-- Compliance Services: client portal access. A grant lets one person sign in (through the shared portal login) and see
-- one client's sent quotes, live agreements and issued invoices. Invite tokens are single use and expire. Revoking
-- takes effect on the next request, because every portal request is checked against the grant.

CREATE TABLE IF NOT EXISTS compliance_portal_grants (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    client_id               UUID NOT NULL REFERENCES compliance_service_clients(id),
    portal_user_id          UUID,
    invite_email            VARCHAR(255) NOT NULL,
    status                  VARCHAR(10) NOT NULL DEFAULT 'PENDING',   -- PENDING, ACTIVE, REVOKED
    invite_token            VARCHAR(100),
    invite_token_expires_at TIMESTAMPTZ,
    email_sent              BOOLEAN NOT NULL DEFAULT FALSE,
    invited_by              UUID,
    invited_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    accepted_at             TIMESTAMPTZ,
    revoked_by              UUID,
    revoked_at              TIMESTAMPTZ,
    CONSTRAINT ck_compliance_portal_status CHECK (status IN ('PENDING', 'ACTIVE', 'REVOKED')),
    CONSTRAINT ck_compliance_portal_active CHECK (status <> 'ACTIVE' OR (portal_user_id IS NOT NULL AND accepted_at IS NOT NULL)),
    CONSTRAINT ck_compliance_portal_token CHECK (status = 'PENDING' OR invite_token IS NULL),
    CONSTRAINT ck_compliance_portal_revoked CHECK (status <> 'REVOKED' OR revoked_at IS NOT NULL)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_portal_token ON compliance_portal_grants(invite_token) WHERE invite_token IS NOT NULL;
-- one live (pending or active) grant for a person at a client; a revoked one lets them be invited again
CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_portal_live ON compliance_portal_grants(tenant_id, client_id, lower(invite_email)) WHERE status <> 'REVOKED';
CREATE INDEX IF NOT EXISTS idx_compliance_portal_user ON compliance_portal_grants(portal_user_id) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_compliance_portal_client ON compliance_portal_grants(tenant_id, client_id);
