-- Secure Document Access Engine (platform-owned; used by Projects, Contracting, Facilities Management).
-- Requires PostgreSQL 13+.
--
-- The engine controls ACCESS to documents only. The module that owns the business record (a project
-- transmittal, a contract pack, a service-report release) keeps that record in its own tables and
-- refers to it here by (module_code, source_type, source_id).
--
-- A SHARE is one controlled release of documents. Each external recipient of a share gets their OWN
-- access token (shown only in their email; only a SHA-256 hash is stored), proves they own the email
-- address with a one-time code, and can then see and download only the items assigned to them until the
-- link expires or is revoked. Every step is written to sda_access_log, which is append-only.
-- Every table carries tenant_id and every query filters on it.

CREATE TABLE IF NOT EXISTS sda_shares (
    id           UUID PRIMARY KEY,
    tenant_id    UUID         NOT NULL,
    module_code  VARCHAR(30)  NOT NULL,
    source_type  VARCHAR(40)  NOT NULL,
    source_id    UUID         NOT NULL,
    context_id   UUID,
    created_by   UUID,
    created_by_name VARCHAR(255),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_sda_share_source ON sda_shares (tenant_id, module_code, source_type, source_id);

-- What was issued, frozen at issue time: the exact document reference and the file hash then.
CREATE TABLE IF NOT EXISTS sda_share_items (
    id                UUID PRIMARY KEY,
    tenant_id         UUID         NOT NULL,
    share_id          UUID         NOT NULL REFERENCES sda_shares (id),
    doc_module_code   VARCHAR(30)  NOT NULL,
    doc_source_type   VARCHAR(40)  NOT NULL,
    doc_source_id     UUID         NOT NULL,
    revision          VARCHAR(50),
    display_number    VARCHAR(100),
    title             VARCHAR(300) NOT NULL,
    status_at_issue   VARCHAR(40),
    file_name         VARCHAR(300),
    file_sha256       VARCHAR(64),
    sort_order        INT          NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_sda_items_share ON sda_share_items (tenant_id, share_id, sort_order);

CREATE TABLE IF NOT EXISTS sda_recipients (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID         NOT NULL,
    share_id            UUID         NOT NULL REFERENCES sda_shares (id),
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
    collaborator_id     UUID,              -- FK added in V578 once sda_collaborators exists
    created_by          UUID,
    created_by_name     VARCHAR(255),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_sda_recipient_token_hash ON sda_recipients (token_hash);
CREATE INDEX IF NOT EXISTS idx_sda_recipients_share ON sda_recipients (tenant_id, share_id);

-- Which items a recipient may see. A recipient with NO rows here may see every item in the share.
CREATE TABLE IF NOT EXISTS sda_recipient_items (
    recipient_id  UUID NOT NULL REFERENCES sda_recipients (id) ON DELETE CASCADE,
    item_id       UUID NOT NULL REFERENCES sda_share_items (id),
    PRIMARY KEY (recipient_id, item_id)
);

-- Append-only trail of everything done with or to a recipient's access.
CREATE TABLE IF NOT EXISTS sda_access_log (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    share_id        UUID         NOT NULL,
    recipient_id    UUID,
    doc_source_id   UUID,
    revision        VARCHAR(50),
    action          VARCHAR(30)  NOT NULL,
    detail          VARCHAR(500),
    ip_address      VARCHAR(64),
    user_agent      VARCHAR(255),
    actor_name      VARCHAR(255),
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sda_log_share ON sda_access_log (tenant_id, share_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_sda_log_recipient ON sda_access_log (recipient_id, occurred_at);

-- Make "append-only" true in the database, not only in the application.
CREATE OR REPLACE FUNCTION sda_block_log_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sda_access_log_append_only ON sda_access_log;
CREATE TRIGGER trg_sda_access_log_append_only
    BEFORE UPDATE OR DELETE ON sda_access_log
    FOR EACH ROW EXECUTE FUNCTION sda_block_log_change();
