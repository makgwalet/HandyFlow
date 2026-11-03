-- Registered collaborators: repeat external users who get ONE standing link to a workspace of every share
-- issued to their email address. Tenant-wide (not tied to one module). Same protections as a single link:
-- hashes only, an emailed one-time code on every visit, immediate disable, append-only trail.

CREATE TABLE IF NOT EXISTS sda_collaborators (
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

CREATE UNIQUE INDEX IF NOT EXISTS uq_sda_collab_token_hash ON sda_collaborators (token_hash);
CREATE UNIQUE INDEX IF NOT EXISTS uq_sda_collab_tenant_email ON sda_collaborators (tenant_id, LOWER(email));

CREATE TABLE IF NOT EXISTS sda_collaborator_log (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL,
    collaborator_id  UUID         NOT NULL REFERENCES sda_collaborators (id),
    action           VARCHAR(30)  NOT NULL,
    detail           VARCHAR(500),
    ip_address       VARCHAR(64),
    user_agent       VARCHAR(255),
    actor_name       VARCHAR(255),
    occurred_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sda_collab_log ON sda_collaborator_log (tenant_id, collaborator_id, occurred_at);

DROP TRIGGER IF EXISTS trg_sda_collab_log_append_only ON sda_collaborator_log;
CREATE TRIGGER trg_sda_collab_log_append_only
    BEFORE UPDATE OR DELETE ON sda_collaborator_log
    FOR EACH ROW EXECUTE FUNCTION sda_block_log_change();

-- Recipients linked to a collaborator account.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_sda_recipient_collaborator') THEN
        ALTER TABLE sda_recipients
            ADD CONSTRAINT fk_sda_recipient_collaborator FOREIGN KEY (collaborator_id) REFERENCES sda_collaborators (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_sda_recipients_collaborator ON sda_recipients (tenant_id, collaborator_id)
    WHERE collaborator_id IS NOT NULL;
