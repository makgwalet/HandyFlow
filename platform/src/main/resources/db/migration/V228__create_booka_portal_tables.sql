CREATE TABLE booka_portal_access_grants (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID NOT NULL,
    client_id                UUID NOT NULL REFERENCES booka_agency_clients(id),
    portal_user_id           UUID,
    invite_email             VARCHAR(255) NOT NULL,
    status                   VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    invite_token             VARCHAR(100) UNIQUE,
    invite_token_expires_at  TIMESTAMPTZ,
    invited_by               UUID,
    invited_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    accepted_at              TIMESTAMPTZ,
    revoked_by               UUID,
    revoked_at               TIMESTAMPTZ
);
CREATE INDEX idx_booka_portal_grants_user ON booka_portal_access_grants (portal_user_id);
CREATE INDEX idx_booka_portal_grants_client ON booka_portal_access_grants (client_id);