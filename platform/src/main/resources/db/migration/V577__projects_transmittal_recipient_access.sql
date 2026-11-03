-- Chunk 6c: secure, per-recipient access to transmitted documents.
-- Requires PostgreSQL 13+.
--
-- Each external recipient of a transmittal gets their OWN access token (shown only in their email; only a
-- SHA-256 hash is stored here), must prove they own the email address with a one-time code, and can then
-- see and download only the document revisions assigned to them, until the link expires or is revoked.
-- Every step is written to project_transmittal_access_log, which is append-only.

CREATE TABLE IF NOT EXISTS project_transmittal_recipients (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID         NOT NULL,
    project_id          UUID         NOT NULL,
    transmittal_id      UUID         NOT NULL REFERENCES project_transmittals (id),
    name                VARCHAR(255) NOT NULL,
    organisation        VARCHAR(255),
    email               VARCHAR(255) NOT NULL,
    token_hash          VARCHAR(64)  NOT NULL,
    can_download        BOOLEAN      NOT NULL DEFAULT TRUE,
    can_acknowledge     BOOLEAN      NOT NULL DEFAULT TRUE,
    expires_at          TIMESTAMPTZ  NOT NULL,
    revoked_at          TIMESTAMPTZ,
    revoked_by_name     VARCHAR(255),
    revoke_reason       VARCHAR(500),
    -- one-time code (hash only), its expiry and the wrong guesses made against it
    otp_hash            VARCHAR(64),
    otp_expires_at      TIMESTAMPTZ,
    otp_attempts        INT          NOT NULL DEFAULT 0,
    otp_last_sent_at    TIMESTAMPTZ,
    -- the session granted after a correct code (hash only)
    session_hash        VARCHAR(64),
    session_expires_at  TIMESTAMPTZ,
    verified_at         TIMESTAMPTZ,
    last_access_at      TIMESTAMPTZ,
    email_sent_at       TIMESTAMPTZ,
    acknowledged_at     TIMESTAMPTZ,
    acknowledged_note   VARCHAR(500),
    created_by          UUID,
    created_by_name     VARCHAR(255),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ptr_token_hash ON project_transmittal_recipients (token_hash);
CREATE INDEX IF NOT EXISTS idx_ptr_transmittal ON project_transmittal_recipients (tenant_id, transmittal_id);
CREATE INDEX IF NOT EXISTS idx_ptr_project ON project_transmittal_recipients (tenant_id, project_id);

-- Which items a recipient may see. A recipient with NO rows here may see every item on the transmittal.
CREATE TABLE IF NOT EXISTS project_transmittal_recipient_items (
    recipient_id  UUID NOT NULL REFERENCES project_transmittal_recipients (id) ON DELETE CASCADE,
    item_id       UUID NOT NULL REFERENCES project_transmittal_items (id),
    PRIMARY KEY (recipient_id, item_id)
);

-- Append-only trail of everything done with or to a recipient's access.
CREATE TABLE IF NOT EXISTS project_transmittal_access_log (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    project_id      UUID         NOT NULL,
    transmittal_id  UUID         NOT NULL,
    recipient_id    UUID,
    document_id     UUID,
    revision        VARCHAR(50),
    action          VARCHAR(30)  NOT NULL,
    detail          VARCHAR(500),
    ip_address      VARCHAR(64),
    user_agent      VARCHAR(255),
    actor_name      VARCHAR(255),
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ptal_transmittal ON project_transmittal_access_log (tenant_id, transmittal_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_ptal_recipient ON project_transmittal_access_log (recipient_id, occurred_at);

-- Recipients created before this migration (single name + email on the transmittal) have no token and
-- therefore no access; send a new transmittal, or add them as a recipient, to give them a link.
