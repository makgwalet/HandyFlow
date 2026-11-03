-- Chunk 6d: registered external collaborators (repeat consultants, contractors, client contacts).
-- Requires PostgreSQL 13+.
--
-- A collaborator has ONE standing link (hash only) that opens a workspace listing the transmittals issued to
-- their address. The link alone is not enough: an emailed one-time code starts a short session, exactly as
-- for a single transmittal link. Accounts are per tenant and can be disabled at once.

CREATE TABLE IF NOT EXISTS project_collaborators (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID         NOT NULL,
    name                VARCHAR(255) NOT NULL,
    organisation        VARCHAR(255),
    email               VARCHAR(255) NOT NULL,
    token_hash          VARCHAR(64)  NOT NULL,
    expires_at          TIMESTAMPTZ  NOT NULL,
    disabled_at         TIMESTAMPTZ,
    disabled_by_name    VARCHAR(255),
    disabled_reason     VARCHAR(500),
    otp_hash            VARCHAR(64),
    otp_expires_at      TIMESTAMPTZ,
    otp_attempts        INT          NOT NULL DEFAULT 0,
    otp_last_sent_at    TIMESTAMPTZ,
    session_hash        VARCHAR(64),
    session_expires_at  TIMESTAMPTZ,
    last_login_at       TIMESTAMPTZ,
    email_sent_at       TIMESTAMPTZ,
    created_by          UUID,
    created_by_name     VARCHAR(255),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_pc_token_hash ON project_collaborators (token_hash);
-- One account per address per tenant (case-insensitive).
CREATE UNIQUE INDEX IF NOT EXISTS uq_pc_tenant_email ON project_collaborators (tenant_id, LOWER(email));

CREATE TABLE IF NOT EXISTS project_collaborator_log (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL,
    collaborator_id  UUID         NOT NULL REFERENCES project_collaborators (id),
    action           VARCHAR(30)  NOT NULL,
    detail           VARCHAR(500),
    ip_address       VARCHAR(64),
    user_agent       VARCHAR(255),
    actor_name       VARCHAR(255),
    occurred_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pcl_collaborator ON project_collaborator_log (tenant_id, collaborator_id, occurred_at);

-- A transmittal recipient whose address matches an enabled collaborator is linked to that account.
ALTER TABLE project_transmittal_recipients
    ADD COLUMN IF NOT EXISTS collaborator_id UUID REFERENCES project_collaborators (id);

CREATE INDEX IF NOT EXISTS idx_ptr_collaborator ON project_transmittal_recipients (tenant_id, collaborator_id)
    WHERE collaborator_id IS NOT NULL;

-- Recipients created before this migration are not linked automatically; they are linked the next time a
-- recipient with the same address is added after the account is registered.
