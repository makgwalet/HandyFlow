-- Fuel Stage 3C: customer portal access. A customer contact is invited against one fuel customer account;
-- the grant (not the login) decides what they can see. Mirrors whse_portal_access_grants.
CREATE TABLE fuel_portal_access_grants (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    account_id              UUID NOT NULL REFERENCES fuel_customer_accounts (id),
    portal_user_id          UUID,
    invite_email            VARCHAR(255) NOT NULL,
    status                  VARCHAR(15) NOT NULL DEFAULT 'PENDING',
    invite_token            VARCHAR(100) UNIQUE,
    invite_token_expires_at TIMESTAMPTZ,
    invited_by              UUID,
    invited_at              TIMESTAMPTZ NOT NULL,
    accepted_at             TIMESTAMPTZ,
    revoked_by              UUID,
    revoked_at              TIMESTAMPTZ,
    CONSTRAINT ck_fuel_portal_grant_status CHECK (status IN ('PENDING', 'ACTIVE', 'REVOKED'))
);

CREATE INDEX idx_fuel_portal_grants_account ON fuel_portal_access_grants (tenant_id, account_id);
CREATE INDEX idx_fuel_portal_grants_user ON fuel_portal_access_grants (portal_user_id) WHERE status = 'ACTIVE';
-- One live invite per email per account, so a contact cannot be invited twice.
CREATE UNIQUE INDEX uq_fuel_portal_grants_live_email ON fuel_portal_access_grants (account_id, lower(invite_email)) WHERE status <> 'REVOKED';
